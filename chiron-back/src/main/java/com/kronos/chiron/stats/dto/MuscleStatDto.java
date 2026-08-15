package com.kronos.chiron.stats.dto;

public record MuscleStatDto(
        String muscle,
        double tonnage,
        int nbSeries,
        int nbSeances) {
}
