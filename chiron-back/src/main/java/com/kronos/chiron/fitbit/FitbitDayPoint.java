package com.kronos.chiron.fitbit;

import java.time.LocalDate;

/**
 * Point journalier du dashboard Fitbit : pas et sommeil d'une date donnée.
 * Alimente les mini-graphes en barres du composant {@code fitbit-dashboard}.
 *
 * @param date        jour concerné
 * @param steps       nombre de pas (null si inconnu)
 * @param sleepHours  heures de sommeil de la nuit (null si inconnu)
 */
public record FitbitDayPoint(LocalDate date, Integer steps, Double sleepHours) {}
