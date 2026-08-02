package com.kronos.chiron.seance.model;

import com.kronos.chiron.exercice.model.ExerciceDefinition;

/**
 * Nature d'un exercice cardio. Détermine les paramètres saisis par l'utilisateur
 * (vitesse/pente pour la marche et la course, distance pour le rameur/SkiErg) et
 * la formule de dépense calorique appliquée par {@code CardioCalorieService}.
 *
 * Une {@link ExerciceDefinition} dont {@code cardioType} est {@code null} est un
 * exercice de musculation classique (séries poids × reps).
 */
public enum CardioType {
    /** Marche sur tapis inclinable — paramètres : durée, vitesse (km/h), pente (%). */
    MARCHE_PENTE,
    /** Course — paramètres : durée, vitesse (km/h), pente (%). */
    COURSE,
    /** Rameur — paramètres : durée, distance (m). */
    RAMEUR,
    /** SkiErg — paramètres : durée, distance (m). */
    SKIERG
}
