package com.kronos.chiron.entity;

import lombok.Getter;

/**
 * Thresholds are minimum ratios (1RM / bodyweight) to reach each tier,
 * indexed from Argonaute (0) to Olympien (6).
 * For TRACTIONS and DIPS, ratio = (bodyweight + extra_weight_1RM) / bodyweight.
 */
@Getter
public enum ExerciseType {

    DEVELOPPE_COUCHE(
            "Développé Couché",
            false,
            RmFormula.BRZYCKI,
            new double[]{0.70, 0.85, 1.00, 1.15, 1.30, 1.45, 1.60}
    ),
    SQUAT(
            "Squat",
            false,
            RmFormula.EPLEY,
            new double[]{0.95, 1.15, 1.30, 1.50, 1.70, 1.90, 2.10}
    ),
    SOULEVE_DE_TERRE(
            "Soulevé de Terre",
            false,
            RmFormula.EPLEY,
            new double[]{1.10, 1.30, 1.50, 1.75, 2.00, 2.20, 2.40}
    ),
    TRACTIONS(
            "Tractions",
            true,
            RmFormula.BRZYCKI,
            new double[]{1.05, 1.15, 1.25, 1.40, 1.55, 1.65, 1.75}
    ),
    DIPS(
            "Dips",
            true,
            RmFormula.BRZYCKI,
            new double[]{1.10, 1.20, 1.35, 1.50, 1.65, 1.80, 1.95}
    );

    private final String nom;
    /** True if bodyweight is added to compute total load (pullups, dips). */
    private final boolean bodyweightExercise;
    /** Formule d'estimation du 1RM adaptée à la biomécanique de l'exercice. */
    private final RmFormula formula;
    /** Min ratio to reach Argonaute[0] through Olympien[6]. */
    private final double[] thresholds;

    ExerciseType(String nom, boolean bodyweightExercise, RmFormula formula, double[] thresholds) {
        this.nom = nom;
        this.bodyweightExercise = bodyweightExercise;
        this.formula = formula;
        this.thresholds = thresholds;
    }
}
