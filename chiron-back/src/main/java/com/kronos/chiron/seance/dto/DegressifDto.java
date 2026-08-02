package com.kronos.chiron.seance.dto;

/**
 * Data Transfer Object representing a single drop set (degressif) of an exercise.
 *
 * @param poids       The weight lifted during this drop set. Can be null if using bodyweight.
 * @param reps        The number of repetitions completed in this drop set.
 */
public record DegressifDto(
        Double poids,
        Integer reps
) {}
