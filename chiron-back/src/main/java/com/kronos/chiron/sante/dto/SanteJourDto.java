package com.kronos.chiron.sante.dto;

import java.time.LocalDate;

public record SanteJourDto(
        LocalDate date,
        Integer pas,
        Double distanceM,
        Integer caloriesTotales,
        Integer caloriesActives,
        Integer minutesZoneActive,
        Integer minutesZoneBruleuse,
        Integer minutesZoneCardio,
        Integer minutesZonePic,
        Integer fcRepos,
        Integer fcMin,
        Double fcMoyenne,
        Integer fcMax,
        Double vfcMs,
        Double vo2Max,
        String niveauAptitude,
        Double chargeCardio,
        Double spo2Pct,
        Double frequenceRespiratoire) {
}
