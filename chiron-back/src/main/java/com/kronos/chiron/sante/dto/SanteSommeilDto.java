package com.kronos.chiron.sante.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SanteSommeilDto(
        LocalDate date,
        LocalDateTime debut,
        LocalDateTime fin,
        boolean sieste,
        boolean stadesDisponibles,
        Integer minutesEndormi,
        Integer minutesEveille,
        Integer minutesAvantEndormissement,
        Integer minutesApresReveil,
        Integer minutesProfond,
        Integer minutesLeger,
        Integer minutesParadoxal,
        Integer minutesAgite,
        Integer nbReveils,
        Double fcSommeilMoyenne,
        Integer score,
        Integer scoreDuree,
        Integer scoreComposition,
        Integer scoreRestauration) {
}
