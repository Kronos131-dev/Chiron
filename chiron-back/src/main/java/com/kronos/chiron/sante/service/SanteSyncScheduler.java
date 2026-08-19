package com.kronos.chiron.sante.service;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SanteSyncScheduler {

    private static final int JOURS_RATTRAPAGE = 3;

    private final SanteSyncService santeSyncService;
    private final UtilisateurRepository utilisateurRepository;

    @Scheduled(cron = "0 30 5 * * *")
    public void syncQuotidien() {
        List<Utilisateur> lies = utilisateurRepository.findByFitbitRefreshTokenEncryptedIsNotNull();
        for (Utilisateur user : lies) {
            santeSyncService.syncRecent(user.getUsername(), JOURS_RATTRAPAGE);
        }
        if (!lies.isEmpty()) {
            log.info("SANTE_SYNC_QUOTIDIEN utilisateurs={}", lies.size());
        }
    }
}
