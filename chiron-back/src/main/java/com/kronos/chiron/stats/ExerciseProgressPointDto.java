package com.kronos.chiron.stats;

import java.time.LocalDate;

public record ExerciseProgressPointDto(
        LocalDate date,
        double chargeMax,
        double e1rm,
        double volume) {
}
