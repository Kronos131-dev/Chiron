package com.kronos.chiron.stats;

public record MuscleStatDto(
        String muscle,
        double tonnage,
        int nbSeries,
        int nbSeances) {
}
