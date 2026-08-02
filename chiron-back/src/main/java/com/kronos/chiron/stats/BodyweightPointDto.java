package com.kronos.chiron.stats;

import java.time.LocalDate;

public record BodyweightPointDto(
        LocalDate date,
        double poids) {
}
