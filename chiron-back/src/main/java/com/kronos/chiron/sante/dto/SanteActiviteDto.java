package com.kronos.chiron.sante.dto;

import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.TypeActivite;

import java.time.LocalDateTime;

public record SanteActiviteDto(
        Long id,
        SourceActivite source,
        TypeActivite typeActivite,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer calories,
        boolean caloriesEstimees,
        Double fcMoyenne,
        Integer fcMin,
        Integer fcMax,
        Integer minutesZoneBasse,
        Integer minutesZoneBruleuse,
        Integer minutesZoneCardio,
        Integer minutesZonePic,
        Integer minutesZoneActive,
        Double chargeCardio,
        Long seanceId,
        boolean enrichissementEnCours) {
}
