package com.kronos.chiron.stats;

import java.time.LocalDate;

/**
 * Un point de composition corporelle (un scan Visbody) avec toutes les métriques
 * et les masses par segment, pour les graphes d'évolution et le schéma corporel.
 */
public record BodyCompositionPointDto(
        LocalDate date,
        Integer note,

        // Composition globale.
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

        // Masse grasse par segment (kg).
        Double mgcBrasGauche,
        Double mgcBrasDroit,
        Double mgcTronc,
        Double mgcJambeGauche,
        Double mgcJambeDroite,

        // Masse musculaire par segment (kg).
        Double muscleBrasGauche,
        Double muscleBrasDroit,
        Double muscleTronc,
        Double muscleJambeGauche,
        Double muscleJambeDroite
) {}
