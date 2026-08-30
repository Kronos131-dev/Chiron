package com.kronos.chiron.course;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

public final class Phrases {

    private static final long SECONDES_PAR_MINUTE = 60;
    private static final long SECONDES_PAR_HEURE = 3600;
    private static final double ALLURE_MIN_PAR_KM_MAX = 99;

    private final Map<String, String> modeles = new HashMap<>();

    private String unite = "minParKm";

    public void fixerUnite(String unite) {
        this.unite = "kmh".equals(unite) ? "kmh" : "minParKm";
    }

    public boolean enKmh() {
        return "kmh".equals(unite);
    }

    public void charger(JSONObject source) {
        if (source == null) return;
        Iterator<String> cles = source.keys();
        while (cles.hasNext()) {
            String cle = cles.next();
            modeles.put(cle, source.optString(cle, ""));
        }
    }

    public String t(String cle) {
        String modele = modeles.get(cle);
        return modele == null ? "" : modele;
    }

    public String t(String cle, Map<String, String> valeurs) {
        String texte = t(cle);
        for (Map.Entry<String, String> entree : valeurs.entrySet()) {
            texte = texte.replace("{{" + entree.getKey() + "}}", entree.getValue());
        }
        return texte;
    }

    public static Double minParKm(double kmh) {
        if (kmh <= 0) return null;
        double minParKm = (double) SECONDES_PAR_HEURE / SECONDES_PAR_MINUTE / kmh;
        return minParKm > ALLURE_MIN_PAR_KM_MAX ? null : minParKm;
    }

    public static double minParKmVersKmh(double minParKm) {
        if (minParKm <= 0) return 0;
        return SECONDES_PAR_MINUTE / minParKm;
    }

    public static String formaterAllure(double kmh) {
        Double minParKm = minParKm(kmh);
        if (minParKm == null) return "—";
        long total = Math.round(minParKm * SECONDES_PAR_MINUTE);
        return String.format(
            Locale.US,
            "%d:%02d",
            total / SECONDES_PAR_MINUTE,
            total % SECONDES_PAR_MINUTE
        );
    }

    public static String formaterChrono(long totalSecondes) {
        long secondes = Math.max(0, totalSecondes);
        long heures = secondes / SECONDES_PAR_HEURE;
        long minutes = (secondes % SECONDES_PAR_HEURE) / SECONDES_PAR_MINUTE;
        long reste = secondes % SECONDES_PAR_MINUTE;
        if (heures > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", heures, minutes, reste);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, reste);
    }

    public static String formaterDistance(double metres) {
        return String.format(Locale.US, "%.2f", metres / Mesure.KM_EN_METRES);
    }

    // WHY: sous le kilometre, « 0.60 kilometres » est illisible a l'oreille. L'annonce se dit
    // donc en metres tant qu'on est en dessous, et le singulier existe parce que « 1 kilometres »
    // s'entend, meme prononce par une machine.
    public String distanceParlee(double metres) {
        Map<String, String> valeurs = new HashMap<>();
        if (metres < Mesure.KM_EN_METRES) {
            valeurs.put("m", String.valueOf(Math.round(metres)));
            return t("metres", valeurs);
        }
        double km = metres / Mesure.KM_EN_METRES;
        valeurs.put(
            "km",
            km == Math.rint(km)
                ? String.valueOf((long) km)
                : String.format(Locale.US, "%.1f", km)
        );
        return t(km == 1 ? "kilometre" : "kilometres", valeurs);
    }

    public String affichageAllure(double kmh) {
        if (!enKmh()) return formaterAllure(kmh);
        return kmh > 0 ? String.format(Locale.US, "%.1f", kmh) : "—";
    }

    public String allureParlee(double kmh) {
        Double minParKm = minParKm(kmh);
        if (minParKm == null) return t("noPace");
        if (enKmh()) {
            Map<String, String> vitesse = new HashMap<>();
            vitesse.put("vitesse", String.format(Locale.FRANCE, "%.1f", kmh));
            return t("speed", vitesse);
        }
        long minutes = (long) Math.floor(minParKm);
        long secondes = Math.round((minParKm - minutes) * SECONDES_PAR_MINUTE);
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("minutes", String.valueOf(minutes));
        valeurs.put("secondes", String.valueOf(secondes));
        return t("pace", valeurs);
    }

    public String dureeParlee(long totalSecondes) {
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("minutes", String.valueOf(totalSecondes / SECONDES_PAR_MINUTE));
        valeurs.put("secondes", String.valueOf(totalSecondes % SECONDES_PAR_MINUTE));
        return t("duration", valeurs);
    }
}
