package com.kronos.chiron.service;

import com.kronos.chiron.entity.ExerciseType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Vérifie le choix de formule par exercice, le plafond de 10 reps et l'extraction du 1RM lesté.
 */
class OneRepMaxEstimatorTest {

    private final OneRepMaxEstimator estimator = new OneRepMaxEstimator();

    @Test
    void bench_usesBrzycki() {
        // Brzycki : 100 × 36/(37-5) = 112.5
        assertThat(estimator.total(ExerciseType.DEVELOPPE_COUCHE, 100.0, 5, 80.0))
                .isCloseTo(112.5, within(0.01));
    }

    @Test
    void squat_usesEpley() {
        // Epley : 100 × (1 + 5/30) = 116.667
        assertThat(estimator.total(ExerciseType.SQUAT, 100.0, 5, 80.0))
                .isCloseTo(116.667, within(0.01));
    }

    @Test
    void deadlift_usesEpley() {
        assertThat(estimator.total(ExerciseType.SOULEVE_DE_TERRE, 100.0, 5, 80.0))
                .isCloseTo(116.667, within(0.01));
    }

    @Test
    void repsCappedAtTen() {
        double at15 = estimator.total(ExerciseType.DEVELOPPE_COUCHE, 100.0, 15, 80.0);
        double at10 = estimator.total(ExerciseType.DEVELOPPE_COUCHE, 100.0, 10, 80.0);
        assertThat(at15).isEqualTo(at10);
        // 100 × 36/27 = 133.33 (et surtout pas la valeur explosée de Brzycki à 15 reps)
        assertThat(at15).isCloseTo(133.33, within(0.01));
    }

    @Test
    void bodyweight_total_includesBodyweight() {
        // Tractions (Brzycki) lest 20 + PC 80 = 100 ; 100 × 36/32 = 112.5
        assertThat(estimator.total(ExerciseType.TRACTIONS, 20.0, 5, 80.0))
                .isCloseTo(112.5, within(0.01));
    }

    @Test
    void bodyweight_display_isLestedOneRm() {
        // 1RM lesté = total (112.5) − PC (80) = 32.5
        assertThat(estimator.display(ExerciseType.TRACTIONS, 20.0, 5, 80.0))
                .isCloseTo(32.5, within(0.01));
    }

    @Test
    void bodyweight_ratio_usesTotalLoad() {
        // ratio tier = total / PC = 112.5 / 80 = 1.40625
        assertThat(estimator.ratio(ExerciseType.TRACTIONS, 20.0, 5, 80.0))
                .isCloseTo(1.40625, within(0.0001));
    }

    @Test
    void bodyweight_nullBodyweight_fallsBackToGeneric() {
        // Sans poids de corps : Epley générique sur la charge saisie = 30 × (1 + 5/30) = 35
        assertThat(estimator.display(ExerciseType.TRACTIONS, 30.0, 5, null))
                .isCloseTo(35.0, within(0.01));
    }

    @Test
    void barbell_display_equalsTotal() {
        double total = estimator.total(ExerciseType.SQUAT, 140.0, 3, 80.0);
        assertThat(estimator.display(ExerciseType.SQUAT, 140.0, 3, 80.0)).isEqualTo(total);
    }

    @Test
    void generic_usesEpleyWithCap() {
        // Epley plafonné : 100 × (1 + 10/30) = 133.33, identique à 20 reps
        assertThat(estimator.generic(100.0, 20)).isCloseTo(133.33, within(0.01));
    }
}
