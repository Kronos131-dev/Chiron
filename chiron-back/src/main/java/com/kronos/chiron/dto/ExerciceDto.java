package com.kronos.chiron.dto;

import java.util.List;

/**
 * Data Transfer Object representing an exercise within a workout session.
 * Encapsulates the exercise name, optional comments, and its nested series (sets).
 *
 * @param nom         The name of the exercise.
 * @param commentaire Optional comment or note associated with the exercise.
 * @param series      The list of sets performed during this exercise.
 */
public record ExerciceDto(
        String nom,
        String commentaire,
        List<SerieDto> series
) {}
