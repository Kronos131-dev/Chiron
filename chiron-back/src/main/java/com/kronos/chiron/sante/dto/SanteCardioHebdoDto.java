package com.kronos.chiron.sante.dto;

import java.time.LocalDate;

public record SanteCardioHebdoDto(
        LocalDate semaineDebut,
        Double chargeCardio,
        Double cibleBasse,
        Double cibleHaute,
        Integer minutesZoneActive) {
}
