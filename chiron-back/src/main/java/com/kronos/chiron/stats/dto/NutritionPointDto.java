package com.kronos.chiron.stats.dto;

import java.time.LocalDate;

public record NutritionPointDto(
        LocalDate date,
        Double kcal,
        Double proteines,
        Double glucides,
        Double lipides,
        Double targetKcal,
        Integer pas) {
}
