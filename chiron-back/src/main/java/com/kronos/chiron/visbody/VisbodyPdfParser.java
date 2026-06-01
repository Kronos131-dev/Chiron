package com.kronos.chiron.visbody;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrait les métriques d'un rapport PDF Visbody « Composition corporelle ».
 *
 * <p>Le parsing est volontairement défensif : on s'appuie sur des ancres de
 * libellés et sur les plages standard {@code [min~max]} pour localiser chaque
 * valeur, et on laisse {@code null} ce qui n'est pas trouvé plutôt que d'échouer.
 */
@Component
public class VisbodyPdfParser {

    private static final DateTimeFormatter DETECTION_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Extrait le texte brut du PDF (ordre de lecture trié par position). */
    public String extractText(byte[] pdf) {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(doc);
        } catch (IOException e) {
            throw new VisbodyParseException("PDF illisible : " + e.getMessage(), e);
        }
    }

    public VisbodyReport parse(byte[] pdf) {
        String text = extractText(pdf);
        // Texte aplati : tous les blancs (espaces, sauts de ligne) → un seul espace.
        String flat = text.replaceAll("\\s+", " ").trim();

        VisbodyReport r = new VisbodyReport();

        // ----- En-tête / identité -----
        r.setIdLabel(group(flat, "ID\\s*:\\s*(.+?)\\s*\\|", 1));
        r.setMaskedEmail(group(flat, "ID\\s*:\\s*.+?\\|\\s*(\\S+)", 1));
        r.setSexe(group(flat, "Sexe\\s*:\\s*(\\p{L}+)", 1));
        r.setTailleCm(dbl(group(flat, "Taille\\s*:\\s*([\\d.]+)\\s*cm", 1)));
        r.setAge(intOf(group(flat, "Age\\s*:\\s*(\\d+)\\s*ans", 1)));
        r.setNote(intOf(group(flat, "Note\\s*(\\d+)", 1)));
        String dt = group(flat, "tection\\s*:\\s*(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})", 1);
        if (dt != null) {
            try { r.setMesureLe(LocalDateTime.parse(dt, DETECTION_FMT)); } catch (Exception ignored) {}
        }

        // ----- Métriques « valeur [plage] » (la valeur précède la plage) -----
        r.setPoids(valueBeforeRange(flat, "Poids kg"));
        r.setMms(valueBeforeRange(flat, "MMS kg"));
        r.setMgc(valueBeforeRange(flat, "MGC kg"));
        r.setTgcPct(valueBeforeRange(flat, "TGC %"));
        r.setImc(valueBeforeRange(flat, "IMC"));
        r.setRth(valueBeforeRange(flat, "RTH"));
        r.setMbKcal(valueBeforeRange(flat, "MB kcal/d"));
        r.setGraisseViscerale(valueBeforeRange(flat, "viscérale"));
        r.setEauIntra(valueBeforeRange(flat, "Eau intracellulaire kg"));
        r.setEauExtra(valueBeforeRange(flat, "Eau extracellulaire kg"));
        r.setRatioEcwTbw(valueBeforeRange(flat, "Ratio ECW/TBW"));
        r.setMasseMusculaire(valueBeforeRange(flat, "Masse musculaire"));
        r.setMmc(valueBeforeRange(flat, "MMC"));
        r.setEauTotale(valueBeforeRange(flat, "Eau Corporelle Totale"));

        // Deuxième valeur de la même ligne du tableau « Aperçu » (valeur [plage] valeur2 [plage]).
        r.setSelInorganique(secondValueBeforeRange(flat, "MMC"));
        r.setMasseProteine(secondValueBeforeRange(flat, "Masse musculaire"));

        // Âge métabolique : valeur sans plage.
        r.setAgeMetabolique(dbl(group(flat, "tabolique\\s+([\\d.]+)", 1)));

        parseSegments(flat, r);

        return r;
    }

    /**
     * Cherche, après le libellé, le premier nombre immédiatement suivi d'une plage
     * {@code [min~max]} : c'est la valeur mesurée. Renvoie null si introuvable.
     */
    private Double valueBeforeRange(String flat, String labelRegex) {
        Matcher m = Pattern.compile("(?:" + labelRegex + ").{0,40}?([\\d]+[.,]?[\\d]*)\\s*\\[")
                .matcher(flat);
        return m.find() ? dbl(m.group(1)) : null;
    }

    /** Deuxième « valeur [plage] » de la ligne (2e colonne du tableau Aperçu). */
    private Double secondValueBeforeRange(String flat, String labelRegex) {
        Matcher m = Pattern.compile(
                "(?:" + labelRegex + ").{0,40}?[\\d]+[.,]?[\\d]*\\s*\\[[^\\]]*\\]\\s*([\\d]+[.,]?[\\d]*)\\s*\\[")
                .matcher(flat);
        return m.find() ? dbl(m.group(1)) : null;
    }

    /**
     * Masses par segment (5 grasse + 5 musculaire).
     *
     * <p><b>Masse grasse</b> : le bloc « Masse graisseuse par segment » du PDF ne
     * contient, une fois retirées les valeurs « x [plage] » du tableau Aperçu, que
     * les 5 décimales des segments, dans l'ordre bras G, bras D, tronc, jambe G,
     * jambe D — extraction fiable.
     *
     * <p><b>Masse musculaire</b> : ce bloc s'entrelace avec les colonnes Analyse/
     * Bilan, donc on s'appuie sur des ancres : « X Gauche Droit Y » (bras),
     * « éval valeur éval » (tronc) et « v1 v2 éval éval » (jambes).
     */
    private void parseSegments(String flat, VisbodyReport r) {
        // ---- Masse grasse : nettoyage de la région dédiée ----
        String grasseRegion = between(flat, "Masse graisseuse par segment", "Analyse muscle-graisse");
        if (grasseRegion != null) {
            String cleaned = grasseRegion.replaceAll("[\\d.]+\\s*\\[[^\\]]*\\]", " ");
            List<Double> vals = allDecimals(cleaned);
            if (vals.size() >= 5) {
                r.setMgcBrasGauche(vals.get(0));
                r.setMgcBrasDroit(vals.get(1));
                r.setMgcTronc(vals.get(2));
                r.setMgcJambeGauche(vals.get(3));
                r.setMgcJambeDroite(vals.get(4));
            }
        }

        // ---- Masse musculaire : ancres ----
        // Bras : 2e occurrence de « X Gauche Droit Y » (la 1re est la masse grasse).
        Matcher bras = Pattern.compile("([\\d.]+)\\s+Gauche\\s+Droit\\s+([\\d.]+)").matcher(flat);
        if (bras.find() && bras.find()) {
            r.setMuscleBrasGauche(dbl(bras.group(1)));
            r.setMuscleBrasDroit(dbl(bras.group(2)));
        }
        // Tronc : 2e « éval valeur éval » (la 1re est la masse grasse du tronc).
        Matcher tronc = Pattern.compile(
                "(?:Basse|Normal|Haute)\\s+([\\d.]+)\\s+(?:Basse|Normal|Haute)").matcher(flat);
        if (tronc.find() && tronc.find()) {
            r.setMuscleTronc(dbl(tronc.group(1)));
        }
        // Jambes : « v1 v2 éval éval » (unique au bloc musculaire).
        Matcher jambes = Pattern.compile(
                "([\\d.]+)\\s+([\\d.]+)\\s+(?:Basse|Normal|Haute)\\s+(?:Basse|Normal|Haute)").matcher(flat);
        if (jambes.find()) {
            r.setMuscleJambeGauche(dbl(jambes.group(1)));
            r.setMuscleJambeDroite(dbl(jambes.group(2)));
        }
    }

    /** Sous-chaîne entre deux ancres (exclues), ou null si l'une manque. */
    private String between(String s, String start, String end) {
        int i = s.indexOf(start);
        if (i < 0) return null;
        int j = s.indexOf(end, i + start.length());
        return j < 0 ? s.substring(i + start.length()) : s.substring(i + start.length(), j);
    }

    /** Toutes les décimales (ex. « 0.21 », « 5.86 ») d'un fragment, dans l'ordre. */
    private List<Double> allDecimals(String s) {
        List<Double> out = new java.util.ArrayList<>();
        Matcher m = Pattern.compile("\\d+[.,]\\d+").matcher(s);
        while (m.find()) out.add(dbl(m.group()));
        return out;
    }

    private String group(String s, String regex, int g) {
        Matcher m = Pattern.compile(regex).matcher(s);
        return m.find() ? m.group(g).trim() : null;
    }

    private Double dbl(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s.replace(',', '.')); } catch (NumberFormatException e) { return null; }
    }

    private Integer intOf(String s) {
        if (s == null) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    /** Erreur de parsing PDF (PDF corrompu / illisible). */
    public static class VisbodyParseException extends RuntimeException {
        public VisbodyParseException(String msg, Throwable cause) { super(msg, cause); }
        public VisbodyParseException(String msg) { super(msg); }
    }
}
