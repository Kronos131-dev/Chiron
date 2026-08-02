package com.kronos.chiron.seance.service;

import com.kronos.chiron.seance.model.CardioType;
import org.springframework.stereotype.Service;

/**
 * Estime la dépense énergétique (kcal) d'un effort cardio à partir des paramètres
 * saisis par l'utilisateur et de son poids de corps.
 *
 * <ul>
 *   <li><b>Marche / Course</b> : équations ACSM (VO2 selon vitesse et pente) →
 *       MET → kcal.</li>
 *   <li><b>Rameur / SkiErg</b> : puissance estimée depuis l'allure (formule
 *       Concept2 {@code watts = 2.80 / pace³}) → kcal.</li>
 * </ul>
 */
@Service
public class CardioCalorieService {

    /** Poids de corps de repli quand l'utilisateur n'a pas renseigné le sien. */
    private static final double DEFAULT_BODYWEIGHT_KG = 70.0;

    /**
     * Estime les calories brûlées pour un effort cardio.
     *
     * @param type      Le type de cardio (détermine la formule appliquée).
     * @param dureeMin  La durée de l'effort en minutes.
     * @param allureKmh La vitesse en km/h (marche / course) ; peut être null.
     * @param pentePct  La pente en pourcentage (marche / course) ; peut être null.
     * @param distanceM La distance en mètres (rameur / SkiErg) ; peut être null.
     * @param poidsKg   Le poids de corps de l'utilisateur en kg (≤ 0 → repli 70 kg).
     * @return Les calories brûlées estimées (≥ 0), ou 0 si les données sont insuffisantes.
     */
    public double estimate(CardioType type, Double dureeMin, Double allureKmh,
                           Double pentePct, Double distanceM, double poidsKg) {
        if (type == null || dureeMin == null || dureeMin <= 0) return 0.0;
        double poids = poidsKg > 0 ? poidsKg : DEFAULT_BODYWEIGHT_KG;

        return switch (type) {
            case MARCHE_PENTE -> metBased(walkingMet(allureKmh, pentePct, 5.0), poids, dureeMin);
            case COURSE       -> metBased(runningMet(allureKmh, pentePct, 9.0), poids, dureeMin);
            case RAMEUR, SKIERG -> ergCalories(distanceM, dureeMin, poids);
        };
    }

    /** kcal = MET × 3.5 × poids(kg) / 200 × durée(min). */
    private double metBased(double met, double poidsKg, double dureeMin) {
        return met * 3.5 * poidsKg / 200.0 * dureeMin;
    }

    /** MET de marche (ACSM) : VO2 = 3.5 + 0.1·v + 1.8·v·g, v en m/min, g pente fractionnaire. */
    private double walkingMet(Double allureKmh, Double pentePct, double defaultKmh) {
        double v = (allureKmh != null && allureKmh > 0 ? allureKmh : defaultKmh) * 1000.0 / 60.0;
        double g = (pentePct != null ? pentePct : 0.0) / 100.0;
        double vo2 = 3.5 + 0.1 * v + 1.8 * v * g;
        return vo2 / 3.5;
    }

    /** MET de course (ACSM) : VO2 = 3.5 + 0.2·v + 0.9·v·g, v en m/min, g pente fractionnaire. */
    private double runningMet(Double allureKmh, Double pentePct, double defaultKmh) {
        double v = (allureKmh != null && allureKmh > 0 ? allureKmh : defaultKmh) * 1000.0 / 60.0;
        double g = (pentePct != null ? pentePct : 0.0) / 100.0;
        double vo2 = 3.5 + 0.2 * v + 0.9 * v * g;
        return vo2 / 3.5;
    }

    /**
     * Calories rameur / SkiErg via la puissance (Concept2).
     * pace = temps(s) / distance(m) ; watts = 2.80 / pace³ ;
     * kcal/h = watts × 3.44 + 300 × (poids / 75) ; kcal = kcal/h × durée(h).
     */
    private double ergCalories(Double distanceM, double dureeMin, double poidsKg) {
        if (distanceM == null || distanceM <= 0) return 0.0;
        double pace = (dureeMin * 60.0) / distanceM;       // secondes par mètre
        double watts = 2.80 / (pace * pace * pace);
        double kcalParHeure = watts * 3.44 + 300.0 * (poidsKg / 75.0);
        return kcalParHeure * (dureeMin / 60.0);
    }
}
