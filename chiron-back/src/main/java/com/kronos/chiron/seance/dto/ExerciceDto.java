package com.kronos.chiron.seance.dto;

import java.util.List;

public record ExerciceDto(
        Long id,
        String nom,
        String commentaire,
        Long exerciceDefinitionId,
        List<SerieDto> series,
        Long blockId,
        String blockType,
        String cardioType,
        boolean unilateral
) {}
