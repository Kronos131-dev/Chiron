package com.kronos.chiron.utilisateur.dto;

/**
 * Préférences de calcul du tonnage de l'utilisateur (conventions de saisie du poids).
 *
 * @param halteresParImplement aux haltères, le poids saisi est celui d'une seule haltère (→ tonnage ×2)
 * @param machineParCote       aux machines, le poids saisi est celui d'un seul côté (→ tonnage ×2)
 */
public record TrainingPrefsDto(boolean halteresParImplement, boolean machineParCote) {}
