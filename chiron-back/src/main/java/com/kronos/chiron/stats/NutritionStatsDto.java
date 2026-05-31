package com.kronos.chiron.stats;

import java.util.Collections;
import java.util.List;

/**
 * Historique nutritionnel sur une période + moyennes, pour la page Statistiques.
 *
 * @param linked         true si le compte Olympus est lié et joignable
 * @param jours          série journalière (ordre chronologique)
 * @param moyKcal        calories moyennes/jour (jours avec données)
 * @param moyProteines   protéines moyennes/jour (g)
 * @param moyGlucides    glucides moyens/jour (g)
 * @param moyLipides     lipides moyens/jour (g)
 * @param moyTargetKcal  objectif calorique moyen/jour
 */
public record NutritionStatsDto(
        boolean linked,
        List<NutritionPointDto> jours,
        Double moyKcal,
        Double moyProteines,
        Double moyGlucides,
        Double moyLipides,
        Double moyTargetKcal
) {
    /** Réponse standard quand le compte Olympus n'est pas lié ou est injoignable. */
    public static NutritionStatsDto notLinked() {
        return new NutritionStatsDto(false, Collections.emptyList(), null, null, null, null, null);
    }
}
