package com.kronos.chiron.stats;

import java.util.Collections;
import java.util.List;

public record NutritionStatsDto(
        boolean linked,
        List<NutritionPointDto> jours,
        Double moyKcal,
        Double moyProteines,
        Double moyGlucides,
        Double moyLipides,
        Double moyTargetKcal) {
    public static NutritionStatsDto notLinked() {
        return new NutritionStatsDto(false, Collections.emptyList(), null, null, null, null, null);
    }
}
