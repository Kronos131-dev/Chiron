package com.kronos.chiron.exportation.service.classeur;

import com.kronos.chiron.exportation.service.classeur.DonneesExport.DegressifDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.ExerciceDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.GenreExercice;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.ModeleDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.SeanceDonnees;
import com.kronos.chiron.exportation.service.classeur.DonneesExport.SerieDonnees;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

final class ProgressionExercices {

    private final Locale locale;
    private final String separateurSeriesHorsMuscu;

    ProgressionExercices(Locale locale, String separateurSeriesHorsMuscu) {
        this.locale = locale;
        this.separateurSeriesHorsMuscu = separateurSeriesHorsMuscu;
    }

    List<LigneProgression> calculer(ModeleDonnees modele, List<SeanceDonnees> seances) {
        Set<String> cles = new LinkedHashSet<>(modele.ordreExercices());
        for (SeanceDonnees seance : seances) {
            for (ExerciceDonnees exercice : seance.exercices()) {
                cles.add(exercice.cle());
            }
        }
        List<LigneProgression> lignes = new ArrayList<>();
        for (String cle : cles) {
            List<ExerciceDonnees> parSeance = new ArrayList<>();
            for (SeanceDonnees seance : seances) {
                parSeance.add(fusionner(seance.exercices().stream().filter(e -> e.cle().equals(cle)).toList()));
            }
            ExerciceDonnees recent = null;
            for (ExerciceDonnees exercice : parSeance) {
                if (exercice != null) recent = exercice;
            }
            if (recent == null) {
                continue;
            }
            String unite = unite(parSeance, recent.genre());
            List<Double> valeurs = new ArrayList<>();
            List<String> series = new ArrayList<>();
            List<String> commentaires = new ArrayList<>();
            for (ExerciceDonnees exercice : parSeance) {
                valeurs.add(exercice == null ? null : valeur(exercice, unite));
                series.add(exercice == null ? "" : textesSeries(exercice));
                commentaires.add(exercice == null || exercice.commentaire() == null ? "" : exercice.commentaire());
            }
            lignes.add(new LigneProgression(cle, recent.nom(), unite, valeurs, series, commentaires));
        }
        return lignes;
    }

    private ExerciceDonnees fusionner(List<ExerciceDonnees> occurrences) {
        if (occurrences.isEmpty()) return null;
        if (occurrences.size() == 1) return occurrences.get(0);
        ExerciceDonnees premiere = occurrences.get(0);
        List<SerieDonnees> series = new ArrayList<>();
        List<String> commentaires = new ArrayList<>();
        for (ExerciceDonnees occurrence : occurrences) {
            series.addAll(occurrence.series());
            if (occurrence.commentaire() != null && !occurrence.commentaire().isBlank()) {
                commentaires.add(occurrence.commentaire());
            }
        }
        return new ExerciceDonnees(premiere.cle(), premiere.nom(), String.join(" / ", commentaires),
                premiere.genre(), series);
    }

    private String unite(List<ExerciceDonnees> occurrences, GenreExercice genre) {
        List<ExerciceDonnees> presentes = occurrences.stream().filter(Objects::nonNull).toList();
        return switch (genre) {
            case WOD -> "tours";
            case CARDIO -> presentes.stream().flatMap(e -> e.series().stream())
                    .anyMatch(s -> s.distanceM() != null && s.distanceM() > 0) ? "km" : "min";
            case MUSCU -> presentes.stream().flatMap(e -> e.series().stream()).anyMatch(s -> s.poids() > 0)
                    ? "kg"
                    : "reps";
        };
    }

    private Double valeur(ExerciceDonnees exercice, String unite) {
        List<SerieDonnees> series = exercice.series();
        if (series.isEmpty()) return null;
        return switch (unite) {
            case "kg" -> series.stream().mapToDouble(SerieDonnees::poids).max().orElse(0);
            case "reps" -> (double) series.stream().mapToInt(SerieDonnees::reps).max().orElse(0);
            case "km" -> arrondir(series.stream().mapToDouble(s -> s.distanceM() == null ? 0 : s.distanceM()).sum()
                    / 1000.0, 2);
            case "min" -> arrondir(series.stream().mapToDouble(s -> s.dureeMin() == null ? 0 : s.dureeMin()).sum(),
                    1);
            default -> (double) series.get(0).reps();
        };
    }

    private double arrondir(double valeur, int decimales) {
        double facteur = Math.pow(10, decimales);
        return Math.round(valeur * facteur) / facteur;
    }

    private String textesSeries(ExerciceDonnees exercice) {
        List<String> textes = new ArrayList<>();
        for (SerieDonnees serie : exercice.series()) {
            switch (exercice.genre()) {
                case WOD -> textes.add(serie.reps() + " tours");
                case CARDIO -> {
                    List<String> parties = new ArrayList<>();
                    if (serie.distanceM() != null && serie.distanceM() > 0) {
                        parties.add(nombre(serie.distanceM() / 1000.0, 2) + " km");
                    }
                    if (serie.dureeMin() != null && serie.dureeMin() > 0) {
                        parties.add(nombre(serie.dureeMin(), 1) + " min");
                    }
                    if (!parties.isEmpty()) textes.add(String.join(" · ", parties));
                }
                case MUSCU -> {
                    if (serie.reps() <= 0 && serie.poids() <= 0) continue;
                    StringBuilder texte = new StringBuilder(chargeEtReps(serie.poids(), serie.reps()));
                    for (DegressifDonnees degressif : serie.degressifs()) {
                        texte.append(" › ").append(chargeEtReps(degressif.poids(), degressif.reps()));
                    }
                    textes.add(texte.toString());
                }
            }
        }
        return String.join(exercice.genre() == GenreExercice.MUSCU ? " · " : separateurSeriesHorsMuscu, textes);
    }

    private String chargeEtReps(double poids, int reps) {
        return (poids > 0 ? nombre(poids, 1) : "PDC") + "×" + reps;
    }

    String nombre(double valeur, int decimalesMax) {
        if (valeur == Math.rint(valeur)) {
            return String.valueOf((long) valeur);
        }
        String texte = String.format(locale, "%." + decimalesMax + "f", valeur);
        String separateur = String.valueOf(DecimalFormatSymbols.getInstance(locale).getDecimalSeparator());
        if (texte.contains(separateur)) {
            texte = texte.replaceAll("0+$", "").replaceAll(Pattern.quote(separateur) + "$", "");
        }
        return texte;
    }
}
