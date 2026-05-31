package com.kronos.chiron.stats;

import java.time.LocalDate;

/**
 * Point de progression d'un exercice pour une séance donnée. On renvoie les trois
 * métriques pour que le front puisse basculer sans recharger.
 *
 * @param date      date de la séance
 * @param chargeMax charge maximale utilisée (kg)
 * @param e1rm      1RM estimé (formule d'Epley) sur la meilleure série
 * @param volume    volume total de l'exercice sur la séance (kg)
 */
public record ExerciseProgressPointDto(
        LocalDate date,
        double chargeMax,
        double e1rm,
        double volume
) {}
