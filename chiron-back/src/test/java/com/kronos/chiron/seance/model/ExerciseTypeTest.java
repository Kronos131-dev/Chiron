package com.kronos.chiron.seance.model;

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
    void course5km_isACourse() {
        assertThat(ExerciseType.COURSE_5KM.isCourse()).isTrue();
        assertThat(ExerciseType.COURSE_5KM.getDistanceKm()).isEqualTo(5.0);
    }

    @Test
    void developpeCouche_isNotACourse() {
        assertThat(ExerciseType.DEVELOPPE_COUCHE.isCourse()).isFalse();
        assertThat(ExerciseType.DEVELOPPE_COUCHE.getDistanceKm()).isNull();
    }

    // WHY: les seuils d'une course sont des vitesses. 25 minutes sur 5 km, c'est 12 km/h — le
    // troisième seuil, celui qui ouvre Myrmidon.
    @Test
    void course5km_vingtCinqMinutes_donneDouzeKilometresHeure() {
        assertThat(ExerciseType.COURSE_5KM.vitesseKmh(1500)).isEqualTo(12.0);
    }

    @Test
    void course10km_cinquanteMinutes_donneDouzeKilometresHeure() {
        assertThat(ExerciseType.COURSE_10KM.vitesseKmh(3000)).isEqualTo(12.0);
    }

    @Test
    void course10km_tempsPourUneVitesse_estLInverseDeLaVitesse() {
        assertThat(ExerciseType.COURSE_10KM.tempsSecondesPour(12.0)).isEqualTo(3000);
    }

    // WHY: le passage du 5 au 10 km coûte de la vitesse (formule de Riegel) : à palier égal, le
    // seuil du 10 km est nécessairement plus bas que celui du 5 km.
    @Test
    void course10km_thresholds_areSlowerThanCourse5km() {
        double[] cinq = ExerciseType.COURSE_5KM.getThresholds();
        double[] dix = ExerciseType.COURSE_10KM.getThresholds();
        for (int i = 0; i < cinq.length; i++) {
            assertThat(dix[i]).isLessThan(cinq[i]);
        }
    }

    @Test
    void allTypes_haveSevenAscendingThresholds() {
        for (ExerciseType type : ExerciseType.values()) {
            assertThat(type.getThresholds()).hasSize(7);
            for (int i = 1; i < type.getThresholds().length; i++) {
                assertThat(type.getThresholds()[i]).isGreaterThan(type.getThresholds()[i - 1]);
            }
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
