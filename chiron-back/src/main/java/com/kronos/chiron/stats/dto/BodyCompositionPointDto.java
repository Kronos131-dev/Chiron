package com.kronos.chiron.stats.dto;

import java.time.LocalDate;

public record BodyCompositionPointDto(
        LocalDate date,
        Integer note,

        Double poids,
        Double masseMusculaire,
        Double mms,
        Double mgc,
        Double mmc,
        Double tgcPct,
        Double imc,
        Double rth,
        Double mbKcal,
        Double ageMetabolique,
        Double graisseViscerale,
        Double eauTotale,
        Double eauIntra,
        Double eauExtra,
        Double ratioEcwTbw,
        Double masseProteine,
        Double selInorganique,

        Double mgcBrasGauche,
        Double mgcBrasDroit,
        Double mgcTronc,
        Double mgcJambeGauche,
        Double mgcJambeDroite,

        Double muscleBrasGauche,
        Double muscleBrasDroit,
        Double muscleTronc,
        Double muscleJambeGauche,
        Double muscleJambeDroite) {
}
