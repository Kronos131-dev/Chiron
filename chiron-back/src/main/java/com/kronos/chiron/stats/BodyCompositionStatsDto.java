package com.kronos.chiron.stats;

import java.util.Collections;
import java.util.List;

public record BodyCompositionStatsDto(
        boolean hasData,
        List<BodyCompositionPointDto> points
) {
    public static BodyCompositionStatsDto empty() {
        return new BodyCompositionStatsDto(false, Collections.emptyList());
    }
}
