package com.kronos.chiron.exportation.service.classeur;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BornesAxeTest {

    private static List<Double> valeurs(Double... valeurs) {
        return new ArrayList<>(Arrays.asList(valeurs));
    }

    @Test
    void pour_progressionDeCharges_zoomeSurLaPlageAvecUnPasLisible() {
        BornesAxe bornes = BornesAxe.pour(valeurs(80.0, 82.5, 85.0, 87.5, 90.0, 92.5));

        assertThat(bornes.minimum()).isEqualTo(75.0);
        assertThat(bornes.maximum()).isEqualTo(100.0);
        assertThat(bornes.pas()).isEqualTo(5.0);
    }

    @Test
    void pour_repetitionsDepuisZero_neDescendJamaisSousZero() {
        BornesAxe bornes = BornesAxe.pour(valeurs(0.0, 5.0, 14.0));

        assertThat(bornes.minimum()).isZero();
        assertThat(bornes.maximum()).isGreaterThanOrEqualTo(14.0);
    }

    @Test
    void pour_valeurUnique_centreLaCourbeAvecUneMargeVisible() {
        BornesAxe bornes = BornesAxe.pour(valeurs(10.0));

        assertThat(bornes.minimum()).isLessThan(10.0);
        assertThat(bornes.maximum()).isGreaterThan(10.0);
        assertThat(bornes.pas()).isPositive();
    }

    @Test
    void pour_valeurUniqueNulle_donneUnAxeNonVide() {
        BornesAxe bornes = BornesAxe.pour(valeurs(0.0));

        assertThat(bornes.minimum()).isZero();
        assertThat(bornes.maximum()).isGreaterThan(0.0);
    }

    @Test
    void pour_ignoreLesSeancesSansValeur() {
        BornesAxe avecTrous = BornesAxe.pour(valeurs(null, 100.0, null, 110.0));
        BornesAxe sansTrous = BornesAxe.pour(valeurs(100.0, 110.0));

        assertThat(avecTrous).isEqualTo(sansTrous);
    }

    @Test
    void pour_aucuneValeur_neDonnePasDeBornes() {
        assertThat(BornesAxe.pour(valeurs(null, null))).isNull();
        assertThat(BornesAxe.pour(List.of())).isNull();
    }

    @Test
    void pour_toujoursUnIntervalleQuiContientToutesLesValeurs() {
        List<List<Double>> series = List.of(valeurs(32.5, 33.0, 35.0), valeurs(1.2, 1.4), valeurs(500.0, 640.0),
                valeurs(0.0, 0.0), valeurs(7.0, 7.0, 7.0), valeurs(0.25, 0.5));
        for (List<Double> serie : series) {
            BornesAxe bornes = BornesAxe.pour(serie);

            assertThat(bornes.minimum()).isLessThanOrEqualTo(serie.stream().min(Double::compare).orElseThrow());
            assertThat(bornes.maximum()).isGreaterThanOrEqualTo(serie.stream().max(Double::compare).orElseThrow());
            assertThat(bornes.maximum()).isGreaterThan(bornes.minimum());
            assertThat(bornes.pas()).isPositive();
        }
    }
}
