package com.kronos.chiron.stats;

import java.time.LocalDate;

public record WeeklyVolumePointDto(
        LocalDate semaine,
        String label,
        double tonnage,
        int nbSeances,
        int nbSeries,
        Double dureeMoyenneMin) {
}
