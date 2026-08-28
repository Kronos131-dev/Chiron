package com.kronos.chiron.seance.service.impl;

import com.kronos.chiron.seance.service.CardioCalorieService;

import com.kronos.chiron.seance.model.CardioType;
import org.springframework.stereotype.Service;

@Service
public class CardioCalorieServiceImpl implements CardioCalorieService {

    private static final double DEFAULT_BODYWEIGHT_KG = 70.0;

    @Override
    public double estimate(CardioType type, Double dureeMin, Double allureKmh,
            Double pentePct, Double distanceM, double poidsKg) {
        if (type == null || dureeMin == null || dureeMin <= 0) return 0.0;
        double poids = poidsKg > 0 ? poidsKg : DEFAULT_BODYWEIGHT_KG;

        return switch (type) {
            case MARCHE_PENTE -> metBased(walkingMet(allureKmh, pentePct, 5.0), poids, dureeMin);
            case COURSE -> metBased(runningMet(allureKmh, pentePct, 9.0), poids, dureeMin);
            case COURSE_EXTERIEUR -> metBased(
                    runningMet(allureMesuree(distanceM, dureeMin, allureKmh), pentePct, 9.0), poids, dureeMin);
            case RAMEUR, SKIERG -> ergCalories(distanceM, dureeMin, poids);
        };
    }

    // WHY: en extérieur la distance est mesurée au GPS, pas saisie. Elle prime donc sur toute
    // allure fournie, qui n'est qu'un repli quand la trace manque.
    private Double allureMesuree(Double distanceM, double dureeMin, Double allureKmh) {
        if (distanceM == null || distanceM <= 0) return allureKmh;
        return distanceM / 1000.0 / (dureeMin / 60.0);
    }

    private double metBased(double met, double poidsKg, double dureeMin) {
        return met * 3.5 * poidsKg / 200.0 * dureeMin;
    }

    private double walkingMet(Double allureKmh, Double pentePct, double defaultKmh) {
        double v = (allureKmh != null && allureKmh > 0 ? allureKmh : defaultKmh) * 1000.0 / 60.0;
        double g = (pentePct != null ? pentePct : 0.0) / 100.0;
        double vo2 = 3.5 + 0.1 * v + 1.8 * v * g;
        return vo2 / 3.5;
    }

    private double runningMet(Double allureKmh, Double pentePct, double defaultKmh) {
        double v = (allureKmh != null && allureKmh > 0 ? allureKmh : defaultKmh) * 1000.0 / 60.0;
        double g = (pentePct != null ? pentePct : 0.0) / 100.0;
        double vo2 = 3.5 + 0.2 * v + 0.9 * v * g;
        return vo2 / 3.5;
    }

    private double ergCalories(Double distanceM, double dureeMin, double poidsKg) {
        if (distanceM == null || distanceM <= 0) return 0.0;
        double pace = (dureeMin * 60.0) / distanceM;
        double watts = 2.80 / (pace * pace * pace);
        double kcalParHeure = watts * 3.44 + 300.0 * (poidsKg / 75.0);
        return kcalParHeure * (dureeMin / 60.0);
    }
}
