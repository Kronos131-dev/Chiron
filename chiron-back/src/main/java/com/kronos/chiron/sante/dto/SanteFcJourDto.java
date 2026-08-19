package com.kronos.chiron.sante.dto;

import java.time.LocalDate;

public record SanteFcJourDto(LocalDate date, Integer fcMin, Double fcMoyenne, Integer fcMax, Integer fcRepos) {
}
