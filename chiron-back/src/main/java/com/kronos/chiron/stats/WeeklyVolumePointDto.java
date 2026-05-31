package com.kronos.chiron.stats;

import java.time.LocalDate;

/**
 * Point hebdomadaire de volume d'entraînement.
 *
 * @param semaine    lundi (date ISO) de la semaine concernée
 * @param label      libellé court « jj/MM » du lundi
 * @param tonnage    volume total soulevé sur la semaine (kg)
 * @param nbSeances  nombre de séances réelles sur la semaine
 * @param nbSeries   nombre total de séries sur la semaine
 */
public record WeeklyVolumePointDto(
        LocalDate semaine,
        String label,
        double tonnage,
        int nbSeances,
        int nbSeries,
        Double dureeMoyenneMin
) {}
