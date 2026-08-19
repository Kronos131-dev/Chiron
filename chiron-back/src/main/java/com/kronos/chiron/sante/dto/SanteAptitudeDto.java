package com.kronos.chiron.sante.dto;

import java.time.LocalDate;

public record SanteAptitudeDto(LocalDate date, Double vo2Max, String niveauAptitude) {
}
