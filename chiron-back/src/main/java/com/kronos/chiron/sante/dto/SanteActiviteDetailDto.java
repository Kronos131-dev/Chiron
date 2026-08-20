package com.kronos.chiron.sante.dto;

import java.util.List;

public record SanteActiviteDetailDto(
        SanteActiviteDto activite,
        List<SanteFcPointDto> pointsFrequenceCardiaque,
        SeuilsCardiaquesDto seuils) {
}
