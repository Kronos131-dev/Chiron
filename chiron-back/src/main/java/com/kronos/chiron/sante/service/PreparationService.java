package com.kronos.chiron.sante.service;

import com.kronos.chiron.utilisateur.model.Utilisateur;

import java.time.LocalDate;

public interface PreparationService {

    Integer calculer(Utilisateur utilisateur, LocalDate date);
}
