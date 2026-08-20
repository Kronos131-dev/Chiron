package com.kronos.chiron.noctua.service;

import com.kronos.chiron.sante.service.ActiviteFusionService;
import com.kronos.chiron.sante.service.SanteSyncService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class NoctuaVeilleScheduler {

    private static final int JOURS_SYNC = 1;
    private static final int JOURS_FUSION = 7;

    private final UtilisateurRepository utilisateurRepository;
    private final SanteSyncService santeSyncService;
    private final ActiviteFusionService activiteFusionService;
    private final NoctuaVeilleService noctuaVeilleService;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void veiller() {
        List<Utilisateur> lies = utilisateurRepository.findByFitbitRefreshTokenEncryptedIsNotNull();
        for (Utilisateur user : lies) {
            try {
                santeSyncService.syncRecent(user.getUsername(), JOURS_SYNC);
                activiteFusionService.fusionnerFenetre(user, JOURS_FUSION);
                noctuaVeilleService.evaluer(user);
            } catch (RuntimeException e) {
                log.warn("NOCTUA_VEILLE_ECHEC user={} : {}", user.getUsername(), e.getMessage());
            }
        }
    }
}
