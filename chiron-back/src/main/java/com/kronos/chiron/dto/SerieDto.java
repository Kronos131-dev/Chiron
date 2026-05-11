package com.kronos.chiron.dto;

/**
 * Data Transfer Object representing a single set (serie) of an exercise.
 * Used for transferring workout data between the client and server.
 *
 * @param poids       The weight lifted during this set. Can be null if using bodyweight.
 * @param reps        The number of repetitions completed in this set.
 * @param commentaire Optional user comment or note regarding this specific set.
 */
public record SerieDto(
        Double poids,
        Integer reps,
        String commentaire
) {}
