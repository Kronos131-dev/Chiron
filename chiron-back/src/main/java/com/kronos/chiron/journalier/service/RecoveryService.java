package com.kronos.chiron.journalier.service;

import java.time.Clock;

import com.kronos.chiron.journalier.model.EtatJournalier;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.journalier.persistence.EtatJournalierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecoveryService {

    private final EtatJournalierRepository repository;


    private final Clock clock;
    @Transactional
    public EtatJournalier upsert(Utilisateur user, LocalDate date,
                                  Double sommeilHeures,
                                  Integer fatigue, Integer courbatures,
                                  Integer stress, Integer energie,
                                  String notes) {
        EtatJournalier etat = repository.findByUtilisateurAndDate(user, date)
                .orElseGet(() -> EtatJournalier.builder()
                        .utilisateur(user)
                        .date(date)
                        .build());

        if (sommeilHeures != null) etat.setSommeilHeures(sommeilHeures);
        if (fatigue != null)       etat.setFatigue(clamp(fatigue));
        if (courbatures != null)   etat.setCourbatures(clamp(courbatures));
        if (stress != null)        etat.setStress(clamp(stress));
        if (energie != null)       etat.setEnergie(clamp(energie));
        if (notes != null && !notes.isBlank()) etat.setNotes(notes.trim());

        return repository.save(etat);
    }

    @Transactional
    public boolean upsertFromFitbit(Utilisateur user, LocalDate date, Double sommeilHeures) {
        if (sommeilHeures == null || sommeilHeures <= 0) {
            return false;
        }
        EtatJournalier etat = repository.findByUtilisateurAndDate(user, date)
                .orElseGet(() -> EtatJournalier.builder()
                        .utilisateur(user)
                        .date(date)
                        .build());

        if (etat.getSommeilHeures() != null) {
            return false;
        }
        etat.setSommeilHeures(sommeilHeures);
        repository.save(etat);
        return true;
    }

    @Transactional(readOnly = true)
    public List<EtatJournalier> getRecent(Utilisateur user, int nbJours) {
        int days = Math.max(1, Math.min(nbJours, 90));
        LocalDate from = LocalDate.now(clock).minusDays(days - 1L);
        return repository.findByUtilisateurAndDateGreaterThanEqualOrderByDateDesc(user, from);
    }

    private Integer clamp(int v) {
        if (v < 1) return 1;
        if (v > 5) return 5;
        return v;
    }
}
