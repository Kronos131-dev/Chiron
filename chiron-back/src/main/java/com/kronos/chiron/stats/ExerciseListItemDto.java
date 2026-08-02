package com.kronos.chiron.stats;

import java.time.LocalDate;

public record ExerciseListItemDto(
        String nom,
        int nbSeances,
        LocalDate derniereSeance) {
}
