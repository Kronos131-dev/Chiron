package com.kronos.chiron.seance.dto;

import com.kronos.chiron.utilisateur.dto.ProfileDto;

import java.time.LocalDateTime;
import java.util.List;

public record SeanceDto(
        Long id,
        String titre,
        String note,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer weekNumber,
        Boolean historique,
        ProfileDto utilisateur,
        List<ExerciceDto> exercices) {
}
