package com.kronos.chiron.stats;

import java.util.List;

/**
 * Répartition de l'entraînement par groupe musculaire sur une période.
 *
 * @param muscles  statistiques par muscle, triées par tonnage décroissant
 * @param negliges groupes musculaires non sollicités sur la période (hors CARDIO)
 */
public record MuscleStatsDto(
        List<MuscleStatDto> muscles,
        List<String> negliges
) {}
