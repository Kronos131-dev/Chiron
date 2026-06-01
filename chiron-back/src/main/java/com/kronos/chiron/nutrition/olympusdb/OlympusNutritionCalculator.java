package com.kronos.chiron.nutrition.olympusdb;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.Period;

/**
 * Réplique le calcul des cibles nutritionnelles d'Olympus (Mifflin-St Jeor), afin que
 * Chiron puisse les dériver directement depuis la base Olympus sans passer par l'API HTTP.
 *
 * <p>Source de vérité : {@code UserMapper.calculateMacros} côté Olympus. Si le calcul
 * automatique est désactivé pour l'utilisateur, on prend ses cibles manuelles.</p>
 */
@Component
public class OlympusNutritionCalculator {

    /** Cibles journalières d'un utilisateur (calories + macros, en kcal / grammes). */
    public record NutritionTargets(Double kcal, Double proteins, Double carbs, Double fats) {
        public static final NutritionTargets EMPTY = new NutritionTargets(null, null, null, null);
    }

    public NutritionTargets computeTargets(OlympusNutritionDao.ProfileRow p) {
        if (p == null) return NutritionTargets.EMPTY;

        Double kcal = null, proteins = null, carbs = null, fats = null;

        boolean autoBaseAvailable = p.currentWeightKg() != null && p.heightCm() != null
                && p.gender() != null && p.activityLevel() != null && p.goal() != null;

        if (autoBaseAvailable) {
            int age = 25;
            if (p.birthDate() != null) {
                age = Period.between(p.birthDate(), LocalDate.now()).getYears();
            }

            // 1. Métabolisme de base (BMR) — Mifflin-St Jeor
            double bmr = (10.0 * p.currentWeightKg()) + (6.25 * p.heightCm()) - (5.0 * age);
            bmr += "MALE".equalsIgnoreCase(p.gender()) ? 5.0 : -161.0;

            // 2. Dépense énergétique journalière (TDEE)
            double multiplier = switch (p.activityLevel().toUpperCase()) {
                case "SEDENTARY" -> 1.2;
                case "LIGHT" -> 1.375;
                case "MODERATE" -> 1.55;
                case "INTENSE" -> 1.725;
                default -> 1.2;
            };
            double tdee = bmr * multiplier;

            // 3. Calories cibles selon l'objectif
            double calorieModifier = switch (p.goal().toUpperCase()) {
                case "LOSE_WEIGHT" -> 0.85;
                case "MAINTAIN" -> 1.0;
                case "GAIN_MUSCLE" -> 1.10;
                default -> 1.0;
            };
            double targetCalories = tdee * calorieModifier;

            // 4. Répartition des macronutriments
            double proteinPerKg = switch (p.goal().toUpperCase()) {
                case "LOSE_WEIGHT" -> 2.0;
                case "MAINTAIN" -> 1.6;
                case "GAIN_MUSCLE" -> 1.8;
                default -> 1.6;
            };
            double prot = p.currentWeightKg() * proteinPerKg;
            double fat = (targetCalories * 0.25) / 9.0;
            double carb = (targetCalories - (prot * 4.0) - (fat * 9.0)) / 4.0;
            if (carb < 0) carb = 0;

            kcal = (double) Math.round(targetCalories);
            proteins = (double) Math.round(prot);
            fats = (double) Math.round(fat);
            carbs = (double) Math.round(carb);
        }

        // Cibles manuelles : écrasent le calcul automatique si désactivé
        if (Boolean.FALSE.equals(p.autoCalculateTargets())) {
            if (p.manualTargetKcal() != null) kcal = p.manualTargetKcal();
            if (p.manualTargetProteins() != null) proteins = p.manualTargetProteins();
            if (p.manualTargetCarbs() != null) carbs = p.manualTargetCarbs();
            if (p.manualTargetFats() != null) fats = p.manualTargetFats();
        }

        return new NutritionTargets(kcal, proteins, carbs, fats);
    }
}
