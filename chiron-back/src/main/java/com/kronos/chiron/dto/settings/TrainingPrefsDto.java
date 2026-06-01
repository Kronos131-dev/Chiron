package com.kronos.chiron.dto.settings;

/**
 * Préférences de saisie d'entraînement de l'utilisateur (cf. calcul du tonnage).
 *
 * @param repsParBras          reps saisies par bras par défaut (sinon total)
 * @param poidsMachineParCote  aux machines, le poids saisi est celui d'un seul côté (→ tonnage ×2)
 */
public record TrainingPrefsDto(boolean repsParBras, boolean poidsMachineParCote) {}
