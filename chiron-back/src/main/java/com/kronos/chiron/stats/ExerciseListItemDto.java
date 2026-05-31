package com.kronos.chiron.stats;

import java.time.LocalDate;

/**
 * Entrée de la liste des exercices réalisés (alimente le sélecteur de progression).
 *
 * @param nom            nom de l'exercice (tel que saisi)
 * @param nbSeances      nombre de séances où l'exercice apparaît
 * @param derniereSeance date de la dernière séance contenant l'exercice
 */
public record ExerciseListItemDto(
        String nom,
        int nbSeances,
        LocalDate derniereSeance
) {}
