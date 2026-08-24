package com.kronos.chiron.seance.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.OptBoolean;
import com.kronos.chiron.utilisateur.dto.ProfileDto;

import java.time.LocalDateTime;
import java.util.List;

public record SeanceDto(
        Long id,
        String titre,
        String note,
        @JsonFormat(lenient = OptBoolean.FALSE) LocalDateTime startTime,
        @JsonFormat(lenient = OptBoolean.FALSE) LocalDateTime endTime,
        Integer weekNumber,
        Boolean historique,
        ProfileDto utilisateur,
        List<ExerciceDto> exercices) {
}
