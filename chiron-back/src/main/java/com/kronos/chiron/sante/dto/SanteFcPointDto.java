package com.kronos.chiron.sante.dto;

import java.time.LocalDateTime;

public record SanteFcPointDto(LocalDateTime horodatage, Integer fcMin, Double fcMoyenne, Integer fcMax) {
}
