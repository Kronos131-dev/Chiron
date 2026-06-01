package com.kronos.chiron.service;

import com.kronos.chiron.entity.ExerciseType;
import com.kronos.chiron.entity.RmFormula;
import org.springframework.stereotype.Component;

/**
 * Source unique d'estimation du 1RM. La formule dépend de l'exercice (cf. {@link ExerciseType}),
 * et le nombre de répétitions est <b>plafonné à {@link #MAX_REPS}</b> : au-delà, l'endurance
 * musculaire fausse complètement toute formule, donc on ne tente pas d'extrapoler.
 *
 * <p>Pour les mouvements lestés (tractions, dips), la charge réellement déplacée est
 * {@code poids_corps + lest}. On estime d'abord le 1RM <i>total</i> (utilisé pour le palier,
 * convention des standards de force), puis on en déduit le 1RM <i>lesté</i> affiché
 * (= total − poids_corps), c'est-à-dire la charge maximale à accrocher à la ceinture.</p>
 */
@Component
public class OneRepMaxEstimator {

    /** Zone de fiabilité : on ne dépasse jamais 10 répétitions pour estimer un 1RM. */
    public static final int MAX_REPS = 10;

    /** 1RM total (charge effective = poids de corps + lest pour les mouvements lestés). */
    public double total(ExerciseType type, double poids, int reps, Double poidsCorps) {
        int r = clampReps(reps);
        double load = (type.isBodyweightExercise() && poidsCorps != null) ? poids + poidsCorps : poids;
        return apply(type.getFormula(), load, r);
    }

    /**
     * 1RM affiché : pour un mouvement lesté, la charge lestée seule (total − poids de corps) ;
     * sinon le 1RM total. Garde-fou : mouvement lesté sans poids de corps connu → estimation
     * générique sur la charge saisie.
     */
    public double display(ExerciseType type, double poids, int reps, Double poidsCorps) {
        if (type.isBodyweightExercise()) {
            if (poidsCorps == null) return generic(poids, reps);
            return total(type, poids, reps, poidsCorps) - poidsCorps;
        }
        return total(type, poids, reps, poidsCorps);
    }

    /** Ratio de performance pour le palier : 1RM total / poids de corps. */
    public double ratio(ExerciseType type, double poids, int reps, double poidsCorps) {
        return total(type, poids, reps, poidsCorps) / poidsCorps;
    }

    /**
     * Estimation générique (Epley, bien bornée en hautes reps), pour les contextes sans
     * {@link ExerciseType} connu (ex. graphes de progression par nom d'exercice).
     */
    public double generic(double poids, int reps) {
        return apply(RmFormula.EPLEY, poids, clampReps(reps));
    }

    private double apply(RmFormula formula, double load, int reps) {
        return switch (formula) {
            case EPLEY -> load * (1.0 + reps / 30.0);
            case BRZYCKI -> load * (36.0 / (37 - reps));
        };
    }

    private int clampReps(int reps) {
        return Math.min(Math.max(reps, 1), MAX_REPS);
    }
}
