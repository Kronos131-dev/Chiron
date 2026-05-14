package com.kronos.chiron.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExerciseTypeTest {

    @Test
    void developeCouche_isNotBodyweight() {
        assertThat(ExerciseType.DEVELOPPE_COUCHE.isBodyweightExercise()).isFalse();
    }

    @Test
    void squat_isNotBodyweight() {
        assertThat(ExerciseType.SQUAT.isBodyweightExercise()).isFalse();
    }

    @Test
    void tractions_isBodyweight() {
        assertThat(ExerciseType.TRACTIONS.isBodyweightExercise()).isTrue();
    }

    @Test
    void dips_isBodyweight() {
        assertThat(ExerciseType.DIPS.isBodyweightExercise()).isTrue();
    }

    @Test
    void souleveDesTerre_hasSevenThresholds() {
        assertThat(ExerciseType.SOULEVE_DE_TERRE.getThresholds()).hasSize(7);
    }

    @Test
    void allTypes_haveNonNullNom() {
        for (ExerciseType type : ExerciseType.values()) {
            assertThat(type.getNom()).isNotBlank();
        }
    }

    @Test
    void developeCouche_thresholds_areAscending() {
        double[] thresholds = ExerciseType.DEVELOPPE_COUCHE.getThresholds();
        for (int i = 1; i < thresholds.length; i++) {
            assertThat(thresholds[i]).isGreaterThan(thresholds[i - 1]);
        }
    }
}
