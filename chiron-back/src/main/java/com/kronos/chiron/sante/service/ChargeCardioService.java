package com.kronos.chiron.sante.service;

import com.kronos.chiron.utilisateur.model.Utilisateur;

import java.time.LocalDate;

public interface ChargeCardioService {

    void recalculerPlage(Utilisateur utilisateur, LocalDate from, LocalDate to);
}
