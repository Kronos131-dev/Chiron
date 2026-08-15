package com.kronos.chiron.stats.dto;

import java.time.LocalDate;

public record BodyweightPointDto(
        LocalDate date,
        double poids) {
}
