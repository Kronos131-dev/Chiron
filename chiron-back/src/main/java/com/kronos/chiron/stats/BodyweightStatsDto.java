package com.kronos.chiron.stats;

import java.util.Collections;
import java.util.List;

/**
 * Historique de poids de corps sur une période.
 *
 * @param linked true si le compte Olympus est lié et joignable
 * @param points série de mesures (ordre chronologique)
 */
public record BodyweightStatsDto(
        boolean linked,
        List<BodyweightPointDto> points
) {
    public static BodyweightStatsDto notLinked() {
        return new BodyweightStatsDto(false, Collections.emptyList());
    }
}
