package com.kronos.chiron.stats;

import java.time.LocalDate;

/**
 * Mesure de poids de corps un jour donné (issue de la base Olympus).
 *
 * @param date  jour de la mesure
 * @param poids poids mesuré (kg)
 */
public record BodyweightPointDto(
        LocalDate date,
        double poids
) {}
