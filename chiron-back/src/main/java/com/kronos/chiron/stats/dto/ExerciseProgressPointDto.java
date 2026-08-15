package com.kronos.chiron.stats.dto;

import java.time.LocalDate;

public record ExerciseProgressPointDto(
        LocalDate date,
        double chargeMax,
        double e1rm,
        double volume) {
}
