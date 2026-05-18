package com.kronos.chiron.service;

import com.kronos.chiron.entity.EtatJournalier;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.EtatJournalierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RecoveryService {

    private final EtatJournalierRepository repository;

    /**
     * Upsert : crée l'état du jour si absent, ou met à jour les champs non-null fournis.
     */
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

    @Transactional(readOnly = true)
    public List<EtatJournalier> getRecent(Utilisateur user, int nbJours) {
        int days = Math.max(1, Math.min(nbJours, 90));
        LocalDate from = LocalDate.now().minusDays(days - 1L);
        return repository.findByUtilisateurAndDateGreaterThanEqualOrderByDateDesc(user, from);
    }

    private Integer clamp(int v) {
        if (v < 1) return 1;
        if (v > 5) return 5;
        return v;
    }
}
