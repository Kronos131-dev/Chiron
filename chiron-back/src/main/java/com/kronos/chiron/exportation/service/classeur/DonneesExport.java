package com.kronos.chiron.exportation.service.classeur;

import java.time.LocalDateTime;
import java.util.List;

public record DonneesExport(String nom, LocalDateTime genereLe, List<ModeleDonnees> modeles) {

    public enum GenreExercice {
        MUSCU, CARDIO, WOD
    }

    public record ModeleDonnees(Long modeleId, String titre, List<String> ordreExercices,
            List<SeanceDonnees> seances) {
    }

    public record SeanceDonnees(Long id, String titre, LocalDateTime date, String note,
            List<ExerciceDonnees> exercices) {
    }

    public record ExerciceDonnees(String cle, String nom, String commentaire, GenreExercice genre,
            List<SerieDonnees> series) {
    }

    public record SerieDonnees(double poids, int reps, Double dureeMin, Double distanceM,
            List<DegressifDonnees> degressifs) {
    }

    public record DegressifDonnees(double poids, int reps) {
    }
}
