package com.kronos.chiron.stats;

import java.util.Collections;
import java.util.List;

/**
 * Historique nutritionnel sur une période + moyennes, pour la page Statistiques.
 *
 * @param linked         true si le compte Olympus est lié
 * @param available      true si la base Olympus a pu être interrogée (false = injoignable)
 * @param jours          série journalière (ordre chronologique)
 * @param moyKcal        calories moyennes/jour (jours avec données)
 * @param moyProteines   protéines moyennes/jour (g)
 * @param moyGlucides    glucides moyens/jour (g)
 * @param moyLipides     lipides moyens/jour (g)
 * @param moyTargetKcal  objectif calorique moyen/jour
 */
public record NutritionStatsDto(
        boolean linked,
        boolean available,
        List<NutritionPointDto> jours,
        Double moyKcal,
        Double moyProteines,
        Double moyGlucides,
        Double moyLipides,
        Double moyTargetKcal
) {
    /** Compte Olympus non lié. */
    public static NutritionStatsDto notLinked() {
        return new NutritionStatsDto(false, true, Collections.emptyList(), null, null, null, null, null);
    }

    /** Compte lié mais base Olympus injoignable (mode dégradé). */
    public static NutritionStatsDto unavailable() {
        return new NutritionStatsDto(true, false, Collections.emptyList(), null, null, null, null, null);
    }
}
