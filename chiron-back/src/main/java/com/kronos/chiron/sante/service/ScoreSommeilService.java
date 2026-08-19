package com.kronos.chiron.sante.service;

import com.kronos.chiron.utilisateur.model.Utilisateur;

import java.time.LocalDate;

public interface ScoreSommeilService {

    void recalculerPlage(Utilisateur utilisateur, LocalDate from, LocalDate to);
}
