package com.kronos.chiron.journalier.service;

import com.kronos.chiron.journalier.model.EtatJournalier;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import java.time.LocalDate;
import java.util.List;

public interface RecoveryService {

    EtatJournalier upsert(Utilisateur user, LocalDate date, Double sommeilHeures, Integer fatigue, Integer courbatures,
            Integer stress, Integer energie, String notes);

    boolean upsertFromFitbit(Utilisateur user, LocalDate date, Double sommeilHeures);

    List<EtatJournalier> getRecent(Utilisateur user, int nbJours);
}
