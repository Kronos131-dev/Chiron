package com.kronos.chiron.stats.dto;

import java.time.LocalDate;

public record ExerciseListItemDto(
        String nom,
        int nbSeances,
        LocalDate derniereSeance) {
}
