package com.kronos.chiron.stats;

public record StatsOverviewDto(
        int seances30,
        int seancesTotal,
        double tonnageSemaine,
        int streakSemaines,
        Double poidsCorps,
        String tier,
        int tierLevel,
        String tierCategorie,
        Double dureeMoyenneMin) {
}
