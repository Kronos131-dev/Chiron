package com.kronos.chiron.fitbit.service.impl;

import com.kronos.chiron.fitbit.client.FitbitClient;
import com.kronos.chiron.fitbit.client.FitbitParser;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.fitbit.service.FitbitSyncService;

import java.time.Clock;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.journalier.service.RecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class FitbitSyncServiceImpl implements FitbitSyncService {

    private final FitbitService fitbitService;
    private final FitbitClient fitbitClient;
    private final RecoveryService recoveryService;
    private final UtilisateurRepository utilisateurRepository;

    private final Clock clock;
    @Override
    public void syncEtatJournalier(String chironUsername, int nbJours) {
        int days = Math.max(1, Math.min(nbJours, 30));
        LocalDate start = LocalDate.now(clock).minusDays(days - 1L);
        try {
            Utilisateur user = utilisateurRepository.findByUsername(chironUsername).orElse(null);
            if (user == null) {
                return;
            }
            String token = fitbitService.getValidToken(chironUsername);
            Map<LocalDate, Double> hoursByDate = FitbitParser.sleepHoursByDate(fitbitClient.listSleep(token, start));

            int written = 0;
            for (Map.Entry<LocalDate, Double> e : hoursByDate.entrySet()) {
                if (recoveryService.upsertFromFitbit(user, e.getKey(), e.getValue())) {
                    written++;
                }
            }
            if (written > 0) {
                log.info("FITBIT_SYNC user={} jours_remplis={}", chironUsername, written);
            }
        } catch (FitbitService.NotLinkedException | FitbitService.ExpiredException e) {
        } catch (RuntimeException e) {
            log.warn("FITBIT_SYNC_FAILED user={} : {}", chironUsername, e.getMessage());
        }
    }
}
