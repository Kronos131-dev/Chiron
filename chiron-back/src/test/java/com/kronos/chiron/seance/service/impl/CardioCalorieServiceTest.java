package com.kronos.chiron.seance.service.impl;

import com.kronos.chiron.seance.service.CardioCalorieService;

import com.kronos.chiron.seance.model.CardioType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CardioCalorieServiceTest {

    private static final double POIDS_KG = 80.0;

    private final CardioCalorieService cardioCalorieService = new CardioCalorieServiceImpl();

    @Test
    void estimate_nullType_returnsZero() {
        assertThat(cardioCalorieService.estimate(null, 30.0, 6.0, 0.0, null, POIDS_KG)).isZero();
    }

    @Test
    void estimate_nullDuration_returnsZero() {
        assertThat(cardioCalorieService.estimate(CardioType.COURSE, null, 12.0, 0.0, null, POIDS_KG))
                .isZero();
    }

    @Test
    void estimate_zeroDuration_returnsZero() {
        assertThat(cardioCalorieService.estimate(CardioType.COURSE, 0.0, 12.0, 0.0, null, POIDS_KG))
                .isZero();
    }

    @Test
    void estimate_negativeDuration_returnsZero() {
        assertThat(cardioCalorieService.estimate(CardioType.COURSE, -10.0, 12.0, 0.0, null, POIDS_KG))
                .isZero();
    }

    @Test
    void estimate_outdoorRun_derivesPaceFromMeasuredDistance() {
        double allureDeduiteKmh = 10000.0 / 1000.0 / (50.0 / 60.0);
        double vitesseMetresParMinute = allureDeduiteKmh * 1000.0 / 60.0;
        double met = (3.5 + 0.2 * vitesseMetresParMinute) / 3.5;
        double expected = met * 3.5 * POIDS_KG / 200.0 * 50.0;

        double actual = cardioCalorieService.estimate(
                CardioType.COURSE_EXTERIEUR, 50.0, null, 0.0, 10000.0, POIDS_KG);

        assertThat(actual).isCloseTo(expected, within(0.0001));
    }

    // WHY: en extérieur la distance vient du GPS. Si une allure saisie primait, une valeur
    // fantaisiste laissée dans le formulaire écraserait la mesure réelle.
    @Test
    void estimate_outdoorRun_measuredDistanceWinsOverProvidedPace() {
        double avecAllureFantaisiste = cardioCalorieService.estimate(
                CardioType.COURSE_EXTERIEUR, 50.0, 25.0, 0.0, 10000.0, POIDS_KG);
        double sansAllure = cardioCalorieService.estimate(
                CardioType.COURSE_EXTERIEUR, 50.0, null, 0.0, 10000.0, POIDS_KG);

        assertThat(avecAllureFantaisiste).isCloseTo(sansAllure, within(0.0001));
    }

    @Test
    void estimate_outdoorRunWithoutDistance_fallsBackOnProvidedPace() {
        double avecAllure = cardioCalorieService.estimate(
                CardioType.COURSE_EXTERIEUR, 45.0, 12.0, 0.0, null, POIDS_KG);
        double surTapis = cardioCalorieService.estimate(
                CardioType.COURSE, 45.0, 12.0, 0.0, null, POIDS_KG);

        assertThat(avecAllure).isCloseTo(surTapis, within(0.0001));
    }

    @Test
    void estimate_outdoorRunUphill_countsTheGradient() {
        double surLePlat = cardioCalorieService.estimate(
                CardioType.COURSE_EXTERIEUR, 50.0, null, 0.0, 10000.0, POIDS_KG);
        double enCote = cardioCalorieService.estimate(
                CardioType.COURSE_EXTERIEUR, 50.0, null, 5.0, 10000.0, POIDS_KG);

        assertThat(enCote).isGreaterThan(surLePlat);
    }

    @Test
    void estimate_walkingOnFlat_appliesAcsmWalkingEquation() {
        double vitesseMetresParMinute = 5.0 * 1000.0 / 60.0;
        double met = (3.5 + 0.1 * vitesseMetresParMinute) / 3.5;
        double expected = met * 3.5 * POIDS_KG / 200.0 * 30.0;

        double actual = cardioCalorieService.estimate(
                CardioType.MARCHE_PENTE, 30.0, 5.0, 0.0, null, POIDS_KG);

        assertThat(actual).isCloseTo(expected, within(0.0001));
    }

    @Test
    void estimate_walkingUphill_burnsMoreThanOnFlat() {
        double flat = cardioCalorieService.estimate(
                CardioType.MARCHE_PENTE, 30.0, 5.0, 0.0, null, POIDS_KG);
        double uphill = cardioCalorieService.estimate(
                CardioType.MARCHE_PENTE, 30.0, 5.0, 10.0, null, POIDS_KG);

        assertThat(uphill).isGreaterThan(flat);
    }

    @Test
    void estimate_walkingWithNullSpeed_fallsBackToFiveKmh() {
        double withNullSpeed = cardioCalorieService.estimate(
                CardioType.MARCHE_PENTE, 30.0, null, 0.0, null, POIDS_KG);
        double withFiveKmh = cardioCalorieService.estimate(
                CardioType.MARCHE_PENTE, 30.0, 5.0, 0.0, null, POIDS_KG);

        assertThat(withNullSpeed).isEqualTo(withFiveKmh);
    }

    @Test
    void estimate_walkingWithNullSlope_isTreatedAsFlat() {
        double withNullSlope = cardioCalorieService.estimate(
                CardioType.MARCHE_PENTE, 30.0, 5.0, null, null, POIDS_KG);
        double withZeroSlope = cardioCalorieService.estimate(
                CardioType.MARCHE_PENTE, 30.0, 5.0, 0.0, null, POIDS_KG);

        assertThat(withNullSlope).isEqualTo(withZeroSlope);
    }

    @Test
    void estimate_runningOnFlat_appliesAcsmRunningEquation() {
        double vitesseMetresParMinute = 12.0 * 1000.0 / 60.0;
        double met = (3.5 + 0.2 * vitesseMetresParMinute) / 3.5;
        double expected = met * 3.5 * POIDS_KG / 200.0 * 45.0;

        double actual = cardioCalorieService.estimate(
                CardioType.COURSE, 45.0, 12.0, 0.0, null, POIDS_KG);

        assertThat(actual).isCloseTo(expected, within(0.0001));
    }

    @Test
    void estimate_runningWithNullSpeed_fallsBackToNineKmh() {
        double withNullSpeed = cardioCalorieService.estimate(
                CardioType.COURSE, 30.0, null, 0.0, null, POIDS_KG);
        double withNineKmh = cardioCalorieService.estimate(
                CardioType.COURSE, 30.0, 9.0, 0.0, null, POIDS_KG);

        assertThat(withNullSpeed).isEqualTo(withNineKmh);
    }

    @Test
    void estimate_runningFasterThanWalking_burnsMore() {
        double marche = cardioCalorieService.estimate(
                CardioType.MARCHE_PENTE, 30.0, 6.0, 0.0, null, POIDS_KG);
        double course = cardioCalorieService.estimate(
                CardioType.COURSE, 30.0, 12.0, 0.0, null, POIDS_KG);

        assertThat(course).isGreaterThan(marche);
    }

    @Test
    void estimate_heavierAthlete_burnsMoreForTheSameEffort() {
        double leger = cardioCalorieService.estimate(CardioType.COURSE, 30.0, 12.0, 0.0, null, 60.0);
        double lourd = cardioCalorieService.estimate(CardioType.COURSE, 30.0, 12.0, 0.0, null, 100.0);

        assertThat(lourd).isGreaterThan(leger);
    }

    @Test
    void estimate_zeroBodyweight_fallsBackToSeventyKilos() {
        double withZero = cardioCalorieService.estimate(CardioType.COURSE, 30.0, 12.0, 0.0, null, 0.0);
        double withSeventy = cardioCalorieService.estimate(
                CardioType.COURSE, 30.0, 12.0, 0.0, null, 70.0);

        assertThat(withZero).isEqualTo(withSeventy);
    }

    @Test
    void estimate_negativeBodyweight_fallsBackToSeventyKilos() {
        double withNegative = cardioCalorieService.estimate(
                CardioType.COURSE, 30.0, 12.0, 0.0, null, -5.0);
        double withSeventy = cardioCalorieService.estimate(
                CardioType.COURSE, 30.0, 12.0, 0.0, null, 70.0);

        assertThat(withNegative).isEqualTo(withSeventy);
    }

    @Test
    void estimate_rowingWithoutDistance_returnsZero() {
        assertThat(cardioCalorieService.estimate(CardioType.RAMEUR, 20.0, null, null, null, POIDS_KG))
                .isZero();
    }

    @Test
    void estimate_rowingWithZeroDistance_returnsZero() {
        assertThat(cardioCalorieService.estimate(CardioType.RAMEUR, 20.0, null, null, 0.0, POIDS_KG))
                .isZero();
    }

    @Test
    void estimate_rowing_appliesConcept2PowerFormula() {
        double pace = (20.0 * 60.0) / 5000.0;
        double watts = 2.80 / (pace * pace * pace);
        double expected = (watts * 3.44 + 300.0 * (POIDS_KG / 75.0)) * (20.0 / 60.0);

        double actual = cardioCalorieService.estimate(
                CardioType.RAMEUR, 20.0, null, null, 5000.0, POIDS_KG);

        assertThat(actual).isCloseTo(expected, within(0.0001));
    }

    @Test
    void estimate_skiErg_usesTheSameFormulaAsRowing() {
        double rameur = cardioCalorieService.estimate(
                CardioType.RAMEUR, 20.0, null, null, 5000.0, POIDS_KG);
        double skiErg = cardioCalorieService.estimate(
                CardioType.SKIERG, 20.0, null, null, 5000.0, POIDS_KG);

        assertThat(skiErg).isEqualTo(rameur);
    }

    @Test
    void estimate_rowingFurtherInTheSameTime_burnsMore() {
        double court = cardioCalorieService.estimate(
                CardioType.RAMEUR, 20.0, null, null, 4000.0, POIDS_KG);
        double long_ = cardioCalorieService.estimate(
                CardioType.RAMEUR, 20.0, null, null, 6000.0, POIDS_KG);

        assertThat(long_).isGreaterThan(court);
    }

    @Test
    void estimate_anyCardioType_neverReturnsNegative() {
        for (CardioType type : CardioType.values()) {
            assertThat(cardioCalorieService.estimate(type, 30.0, 8.0, 5.0, 5000.0, POIDS_KG))
                    .isGreaterThanOrEqualTo(0.0);
        }
    }
}
