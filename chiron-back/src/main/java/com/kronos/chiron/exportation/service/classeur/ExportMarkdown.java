package com.kronos.chiron.exportation.service.classeur;

import com.kronos.chiron.exportation.service.classeur.DonneesExport.ModeleDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.SeanceDonnees;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Component
public class ExportMarkdown {

    private static final DateTimeFormatter JOUR_COMPLET = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter JOUR_COURT = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter JOUR_COURT_AVEC_ANNEE = DateTimeFormatter.ofPattern("dd/MM/yy");
    private static final int DECIMALES = 2;
    private static final String SEPARATEUR = "  ";

    private final ProgressionExercices progressions = new ProgressionExercices(Locale.ROOT, " ; ");

    public String generer(DonneesExport donnees) {
        DateTimeFormatter jour = plusieursAnnees(donnees) ? JOUR_COURT_AVEC_ANNEE : JOUR_COURT;
        StringBuilder texte = new StringBuilder();
        entete(texte, donnees);
        for (ModeleDonnees modele : donnees.modeles()) {
            texte.append('\n');
            section(texte, modele, jour);
        }
        return texte.toString();
    }

    private void entete(StringBuilder texte, DonneesExport donnees) {
        texte.append("# ").append(uneLigne(donnees.nom())).append(" — export du ")
                .append(donnees.genereLe().format(JOUR_COMPLET)).append('\n');
        texte.append("Données d'entraînement, du plus ancien au plus récent. Unités : kg, reps, km, min, tours.\n");
        texte.append("PDC = poids du corps. A×B = charge × répétitions. › = série dégressive.\n");
        texte.append("max = meilleure charge de la séance (reps max au poids du corps, distance ou durée en"
                + " cardio).\n");
    }

    private void section(StringBuilder texte, ModeleDonnees modele, DateTimeFormatter jour) {
        List<SeanceDonnees> seances = new ArrayList<>(modele.seances());
        seances.sort(Comparator.comparing(SeanceDonnees::date));
        texte.append("## ").append(uneLigne(modele.titre()));
        if (seances.isEmpty()) {
            texte.append(" — aucune séance enregistrée\n");
            return;
        }
        texte.append(" — ").append(compte(seances.size())).append(", ")
                .append(seances.get(0).date().format(JOUR_COMPLET)).append(" → ")
                .append(seances.get(seances.size() - 1).date().format(JOUR_COMPLET)).append('\n');
        for (LigneProgression ligne : progressions.calculer(modele, seances)) {
            exercice(texte, ligne, seances, jour);
        }
        notes(texte, seances, jour);
    }

    private void exercice(StringBuilder texte, LigneProgression ligne, List<SeanceDonnees> seances,
            DateTimeFormatter jour) {
        texte.append("### ").append(uneLigne(ligne.nom())).append(" [").append(ligne.unite()).append("]");
        texte.append(synthese(ligne, seances, jour)).append('\n');
        for (int i = 0; i < seances.size(); i++) {
            Double valeur = ligne.valeurs().get(i);
            if (valeur == null) {
                continue;
            }
            List<String> parties = new ArrayList<>();
            parties.add(seances.get(i).date().format(jour));
            if (!ligne.series().get(i).isEmpty()) {
                parties.add(ligne.series().get(i));
            }
            parties.add("max " + progressions.nombre(valeur, DECIMALES));
            String commentaire = uneLigne(ligne.commentaires().get(i));
            if (!commentaire.isEmpty()) {
                parties.add("« " + commentaire + " »");
            }
            texte.append(String.join(SEPARATEUR, parties)).append('\n');
        }
    }

    private String synthese(LigneProgression ligne, List<SeanceDonnees> seances, DateTimeFormatter jour) {
        int compte = 0;
        Double premiere = null;
        Double derniere = null;
        Double record = null;
        int indexRecord = -1;
        for (int i = 0; i < ligne.valeurs().size(); i++) {
            Double valeur = ligne.valeurs().get(i);
            if (valeur == null) {
                continue;
            }
            compte++;
            if (premiere == null) {
                premiere = valeur;
            }
            derniere = valeur;
            if (record == null || valeur > record) {
                record = valeur;
                indexRecord = i;
            }
        }
        if (compte <= 1) {
            return " — " + compte(compte);
        }
        return " — " + compte(compte) + ", " + progressions.nombre(premiere, DECIMALES) + " → "
                + progressions.nombre(derniere, DECIMALES) + ", record " + progressions.nombre(record, DECIMALES)
                + " (" + seances.get(indexRecord).date().format(jour) + ")";
    }

    private void notes(StringBuilder texte, List<SeanceDonnees> seances, DateTimeFormatter jour) {
        String notes = seances.stream()
                .filter(seance -> !uneLigne(seance.note()).isEmpty())
                .map(seance -> seance.date().format(jour) + " « " + uneLigne(seance.note()) + " »")
                .collect(Collectors.joining(" ; "));
        if (!notes.isEmpty()) {
            texte.append("Notes : ").append(notes).append('\n');
        }
    }

    private boolean plusieursAnnees(DonneesExport donnees) {
        return donnees.modeles().stream().flatMap(modele -> modele.seances().stream())
                .map(seance -> seance.date().getYear()).distinct().count() > 1;
    }

    private String compte(int nombre) {
        return nombre + (nombre > 1 ? " séances" : " séance");
    }

    private String uneLigne(String texte) {
        return texte == null ? "" : texte.replaceAll("\\s+", " ").trim();
    }
}
