package com.kronos.chiron.dto;

import java.util.List;

public record ExerciceDto(
        Long id,
        String nom,
        String commentaire,
        Long exerciceDefinitionId,
        List<SerieDto> series
) {}
