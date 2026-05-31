package com.kronos.chiron.stats;

/**
 * Statistiques agrégées pour un groupe musculaire sur une période.
 *
 * @param muscle    nom du groupe musculaire (enum MuscleGroup)
 * @param tonnage   volume total soulevé pour ce muscle (kg)
 * @param nbSeries  nombre total de séries
 * @param nbSeances nombre de séances distinctes sollicitant ce muscle
 */
public record MuscleStatDto(
        String muscle,
        double tonnage,
        int nbSeries,
        int nbSeances
) {}
