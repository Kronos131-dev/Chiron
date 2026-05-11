package com.kronos.chiron.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Data Transfer Object representing a comprehensive workout session.
 * Used to transfer full session details including the owning user and nested exercises.
 *
 * @param id          The unique identifier of the session.
 * @param titre       The title or name of the workout session.
 * @param startTime   The timestamp when the session began.
 * @param endTime     The timestamp when the session ended.
 * @param weekNumber  The week number associated with this session schedule.
 * @param isModele    Indicates whether this session is a template (true) or an executed historical session (false).
 * @param utilisateur Profile information of the user who owns this session.
 * @param exercices   The list of exercises performed during this session.
 */
public record SeanceDto(
        Long id,
        String titre,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer weekNumber,
        Boolean isModele,
        ProfileDto utilisateur,
        List<ExerciceDto> exercices
) {}
