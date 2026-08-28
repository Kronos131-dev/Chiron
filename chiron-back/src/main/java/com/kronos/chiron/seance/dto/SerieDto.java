package com.kronos.chiron.seance.dto;

import java.util.List;

public record SerieDto(
        Double poids,
        Integer reps,
        String commentaire,
        List<DegressifDto> degressifs,
        Double dureeMin,
        Double distanceM,
        Double allureKmh,
        Double pentePct,
        Double calories,
        Long courseTraceId) {
}
