package com.kronos.chiron.stats;

import java.util.Collections;
import java.util.List;

public record BodyweightStatsDto(
        boolean linked,
        List<BodyweightPointDto> points) {
    public static BodyweightStatsDto notLinked() {
        return new BodyweightStatsDto(false, Collections.emptyList());
    }
}
