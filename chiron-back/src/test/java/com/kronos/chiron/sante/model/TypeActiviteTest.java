package com.kronos.chiron.sante.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TypeActiviteTest {

    @Test
    void fromGoogle_walking_isMarche() {
        assertThat(TypeActivite.fromGoogle("WALKING")).isEqualTo(TypeActivite.MARCHE);
    }

    @Test
    void fromGoogle_running_isCourse() {
        assertThat(TypeActivite.fromGoogle("RUNNING")).isEqualTo(TypeActivite.COURSE);
    }

    @Test
    void fromGoogle_biking_isVelo() {
        assertThat(TypeActivite.fromGoogle("BIKING")).isEqualTo(TypeActivite.VELO);
        assertThat(TypeActivite.fromGoogle("CYCLING")).isEqualTo(TypeActivite.VELO);
    }

    @Test
    void fromGoogle_soccer_isFootball() {
        assertThat(TypeActivite.fromGoogle("SOCCER")).isEqualTo(TypeActivite.FOOTBALL);
    }

    @Test
    void fromGoogle_weightMachines_isMusculation() {
        assertThat(TypeActivite.fromGoogle("WEIGHT_MACHINES")).isEqualTo(TypeActivite.MUSCULATION);
    }

    @Test
    void fromGoogle_strengthTraining_isMusculation() {
        assertThat(TypeActivite.fromGoogle("STRENGTH_TRAINING")).isEqualTo(TypeActivite.MUSCULATION);
    }

    @Test
    void fromGoogle_crossfit_isMusculation() {
        assertThat(TypeActivite.fromGoogle("CROSSFIT")).isEqualTo(TypeActivite.MUSCULATION);
    }

    @Test
    void fromGoogle_genericSport_fallsBackToSportAutre() {
        assertThat(TypeActivite.fromGoogle("SPORT")).isEqualTo(TypeActivite.SPORT_AUTRE);
    }

    @Test
    void fromGoogle_unknownValue_fallsBackToSportAutre() {
        assertThat(TypeActivite.fromGoogle("SWIMMING")).isEqualTo(TypeActivite.SPORT_AUTRE);
    }

    @Test
    void fromGoogle_null_fallsBackToSportAutre() {
        assertThat(TypeActivite.fromGoogle(null)).isEqualTo(TypeActivite.SPORT_AUTRE);
    }
}
