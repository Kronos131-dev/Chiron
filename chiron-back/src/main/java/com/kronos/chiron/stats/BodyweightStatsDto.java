package com.kronos.chiron.stats;

import java.util.Collections;
import java.util.List;

/**
 * Historique de poids de corps sur une période.
 *
 * @param linked    true si le compte Olympus est lié
 * @param available true si la base Olympus a pu être interrogée (false = injoignable)
 * @param points    série de mesures (ordre chronologique)
 */
public record BodyweightStatsDto(
        boolean linked,
        boolean available,
        List<BodyweightPointDto> points
) {
    public static BodyweightStatsDto notLinked() {
        return new BodyweightStatsDto(false, true, Collections.emptyList());
    }

    public static BodyweightStatsDto unavailable() {
        return new BodyweightStatsDto(true, false, Collections.emptyList());
    }
}
