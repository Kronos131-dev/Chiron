package com.kronos.chiron.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Data Transfer Object representing a lightweight summary of a workout session.
 * Used for listing sessions efficiently without loading full nested exercise details.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeanceSummaryDto {

    /**
     * The unique identifier of the session.
     */
    private Long id;

    /**
     * The title or name of the workout session.
     */
    private String titre;

    /**
     * The timestamp when the session began.
     */
    private LocalDateTime startTime;

    /**
     * The total number of unique exercises performed in this session.
     */
    private int numberOfExercises;

    /**
     * The total aggregate number of sets (series) performed across all exercises in this session.
     */
    private int totalSeries;

    /**
     * Indicates whether this session is a template (true) or an executed historical session (false).
     */
    private boolean isModele;
}
