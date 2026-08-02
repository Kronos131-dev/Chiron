package com.kronos.chiron.seance.service;

import com.kronos.chiron.seance.model.CardioType;

public interface CardioCalorieService {

    double estimate(CardioType type, Double dureeMin, Double allureKmh, Double pentePct, Double distanceM,
            double poidsKg);
}
