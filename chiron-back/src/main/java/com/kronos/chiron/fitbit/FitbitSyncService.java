package com.kronos.chiron.fitbit;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.journalier.service.RecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Map;

/**
 * Pré-remplit {@code etat_journalier.sommeil_heures} depuis le sommeil Fitbit
 * (via la Google Health API). La sync est <b>best-effort</b> : toute erreur
 * (non lié, expiré, injoignable) est avalée — elle ne doit jamais casser
 * l'appelant (dashboard ou outil IA).
 *
 * <p>Dépend de {@link FitbitService} pour le token ; {@code FitbitService} ne
 * dépend volontairement PAS de ce service (pas de cycle de beans).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FitbitSyncService {

    private final FitbitService fitbitService;
    private final FitbitClient fitbitClient;
    private final RecoveryService recoveryService;
    private final UtilisateurRepository utilisateurRepository;

    /**
     * Synchronise le sommeil Fitbit des {@code nbJours} derniers jours dans
     * {@code etat_journalier}. La saisie manuelle n'est jamais écrasée
     * (cf. {@link RecoveryService#upsertFromFitbit}).
     */
    public void syncEtatJournalier(String chironUsername, int nbJours) {
        int days = Math.max(1, Math.min(nbJours, 30));
        LocalDate start = LocalDate.now().minusDays(days - 1L);
        try {
            Utilisateur user = utilisateurRepository.findByUsername(chironUsername).orElse(null);
            if (user == null) {
                return;
            }
            String token = fitbitService.getValidToken(chironUsername);
            Map<LocalDate, Double> hoursByDate =
                    FitbitParser.sleepHoursByDate(fitbitClient.listSleep(token, start));

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
            // Compte non lié ou liaison expirée : rien à synchroniser, silencieux.
        } catch (RuntimeException e) {
            log.warn("FITBIT_SYNC_FAILED user={} : {}", chironUsername, e.getMessage());
        }
    }
}
