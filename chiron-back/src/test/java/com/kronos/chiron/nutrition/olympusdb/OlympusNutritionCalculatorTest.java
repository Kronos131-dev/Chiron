package com.kronos.chiron.nutrition.olympusdb;

import com.kronos.chiron.nutrition.olympusdb.OlympusNutritionCalculator.NutritionTargets;
import com.kronos.chiron.nutrition.olympusdb.OlympusNutritionDao.ProfileRow;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.Period;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie que le calcul des cibles réplique exactement la formule Mifflin-St Jeor d'Olympus
 * ({@code UserMapper.calculateMacros}).
 */
class OlympusNutritionCalculatorTest {

    private final OlympusNutritionCalculator calculator = new OlympusNutritionCalculator();

    @Test
    void computeTargets_autoMode_matchesOlympusFormula() {
        LocalDate birth = LocalDate.now().minusYears(30);
        ProfileRow prof = new ProfileRow(
                80.0, 180.0, "MALE", birth, "MODERATE", "LOSE_WEIGHT",
                true, null, null, null, null);

        NutritionTargets t = calculator.computeTargets(prof);

        // Réplique manuelle de la formule Olympus
        int age = Period.between(birth, LocalDate.now()).getYears();
        double bmr = 10 * 80 + 6.25 * 180 - 5 * age + 5;
        double tdee = bmr * 1.55;
        double kcal = tdee * 0.85;
        double prot = 80 * 2.0;
        double fat = (kcal * 0.25) / 9.0;
        double carb = (kcal - prot * 4 - fat * 9) / 4.0;

        assertThat(t.kcal()).isEqualTo((double) Math.round(kcal));
        assertThat(t.proteins()).isEqualTo((double) Math.round(prot));
        assertThat(t.fats()).isEqualTo((double) Math.round(fat));
        assertThat(t.carbs()).isEqualTo((double) Math.round(carb));
    }

    @Test
    void computeTargets_femaleMaintain_usesCorrectConstants() {
        LocalDate birth = LocalDate.now().minusYears(25);
        ProfileRow prof = new ProfileRow(
                60.0, 165.0, "FEMALE", birth, "SEDENTARY", "MAINTAIN",
                true, null, null, null, null);

        NutritionTargets t = calculator.computeTargets(prof);

        double bmr = 10 * 60 + 6.25 * 165 - 5 * 25 - 161;
        double kcal = bmr * 1.2 * 1.0;
        assertThat(t.kcal()).isEqualTo((double) Math.round(kcal));
        assertThat(t.proteins()).isEqualTo((double) Math.round(60 * 1.6));
    }

    @Test
    void computeTargets_manualMode_overridesAuto() {
        ProfileRow prof = new ProfileRow(
                80.0, 180.0, "MALE", LocalDate.now().minusYears(30), "MODERATE", "MAINTAIN",
                false, 2200.0, 180.0, 200.0, 60.0);

        NutritionTargets t = calculator.computeTargets(prof);

        assertThat(t.kcal()).isEqualTo(2200.0);
        assertThat(t.proteins()).isEqualTo(180.0);
        assertThat(t.carbs()).isEqualTo(200.0);
        assertThat(t.fats()).isEqualTo(60.0);
    }

    @Test
    void computeTargets_nullProfile_returnsEmpty() {
        assertThat(calculator.computeTargets(null)).isEqualTo(NutritionTargets.EMPTY);
    }
}
