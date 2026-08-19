package com.kronos.chiron.sante.dto;

import java.time.LocalDate;

public record SanteResumeDto(
        boolean linked,
        boolean needsReconnect,
        LocalDate date,
        Integer pas,
        Double distanceM,
        Integer caloriesTotales,
        Integer caloriesActives,
        Integer minutesZoneActive,
        Integer fcRepos,
        Integer scoreSommeil,
        Double chargeCardioHebdo) {

    public static SanteResumeDto notLinked() {
        return new SanteResumeDto(false, false, null, null, null, null, null, null, null, null, null);
    }

    public static SanteResumeDto reconnectNeeded() {
        return new SanteResumeDto(true, true, null, null, null, null, null, null, null, null, null);
    }
}
