package com.kronos.chiron.entity;

/**
 * Formule d'estimation du 1RM associée à un exercice. La biomécanique diffère : Epley est
 * plus fiable sur les gros mouvements du bas du corps (squat, soulevé de terre), Brzycki sur
 * les mouvements de poussée du haut du corps (développé couché) et les mouvements lestés.
 */
public enum RmFormula {
    /** 1RM = charge × (1 + reps/30). */
    EPLEY,
    /** 1RM = charge × 36/(37 − reps). */
    BRZYCKI
}
