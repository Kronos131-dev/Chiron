package com.kronos.chiron.stats;

import java.time.LocalDate;

/**
 * Apport nutritionnel d'une journée (issu directement de la base Olympus).
 *
 * @param date       jour concerné
 * @param kcal       calories consommées
 * @param proteines  protéines consommées (g)
 * @param glucides   glucides consommés (g)
 * @param lipides    lipides consommés (g)
 * @param targetKcal objectif calorique du jour (dernier objectif connu), peut être null
 * @param pas        nombre de pas du jour, peut être null
 */
public record NutritionPointDto(
        LocalDate date,
        Double kcal,
        Double proteines,
        Double glucides,
        Double lipides,
        Double targetKcal,
        Integer pas
) {}
