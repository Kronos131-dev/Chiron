package com.kronos.chiron.stats;

import java.util.Collections;
import java.util.List;

/**
 * Historique de composition corporelle (scans Visbody) sur une période.
 *
 * @param hasData true s'il existe au moins un scan
 * @param points  série de scans (ordre chronologique)
 */
public record BodyCompositionStatsDto(
        boolean hasData,
        List<BodyCompositionPointDto> points
) {
    public static BodyCompositionStatsDto empty() {
        return new BodyCompositionStatsDto(false, Collections.emptyList());
    }
}
