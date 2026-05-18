package com.kronos.chiron.ai;

/**
 * Specification d'un exercice à insérer dans un programme créé par l'IA.
 * Le nom doit correspondre à un exercice de la bibliothèque standardisée.
 */
public record ProgrammeExerciceSpec(String nomExercice, Integer nbSeries, Integer reps) {}
