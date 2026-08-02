package com.kronos.chiron.stats;

import java.util.List;

public record MuscleStatsDto(
        List<MuscleStatDto> muscles,
        List<String> negliges) {
}
