package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.service.ActiviteEnrichissementService;
import com.kronos.chiron.sante.service.ActiviteFusionService;
import com.kronos.chiron.sante.service.SanteSyncService;
import com.kronos.chiron.seance.model.Seance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActiviteEnrichissementServiceImpl implements ActiviteEnrichissementService {

    private static final Duration[] BACKOFF = {Duration.ofMinutes(2), Duration.ofMinutes(10),
            Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6)};

    private final SanteActiviteRepository santeActiviteRepository;
    private final FitbitService fitbitService;
    private final SanteSyncService santeSyncService;
    private final ActiviteFusionService activiteFusionService;
    private final Clock clock;

    @Override
    @Transactional
    public void planifierEnrichissement(Seance seance) {
        SanteActivite activite = SanteActivite.builder()
                .utilisateur(seance.getUtilisateur())
                .seance(seance)
                .source(SourceActivite.CHIRON_MUSCU)
                .typeActivite(TypeActivite.MUSCULATION)
                .startTime(seance.getStartTime())
                .endTime(seance.getEndTime())
                .statutEnrichissement(StatutEnrichissement.EN_ATTENTE)
                .tentativesEnrichissement(0)
                .prochaineTentativeAt(LocalDateTime.now(clock))
                .build();
        santeActiviteRepository.save(activite);
    }

    @Override
    @Transactional
    public void tenterEnrichissement(Long activiteId) {
        SanteActivite activite = santeActiviteRepository.findById(activiteId).orElse(null);
        if (activite == null || activite.getStatutEnrichissement() != StatutEnrichissement.EN_ATTENTE) {
            return;
        }

        String username = activite.getUtilisateur().getUsername();
        try {
            fitbitService.getValidToken(username);
        } catch (FitbitService.NotLinkedException | FitbitService.ExpiredException e) {
            abandonner(activite, "Compte Google Health non lié ou expiré.");
            return;
        }

        try {
            santeSyncService.syncRecent(username, 1);
        } catch (RuntimeException e) {
            log.warn("SANTE_ACTIVITE_SYNC_ECHEC activiteId={} message={}", activiteId, e.getMessage());
        }

        if (activite.getStatutEnrichissement() == StatutEnrichissement.COMPLET) {
            return;
        }

        activiteFusionService.fusionnerActivite(activite);

        boolean donneesTrouvees = activite.getFcMoyenne() != null && activite.getChargeCardio() != null;
        int tentative = activite.getTentativesEnrichissement() + 1;
        activite.setTentativesEnrichissement(tentative);

        if (donneesTrouvees) {
            activite.setStatutEnrichissement(StatutEnrichissement.COMPLET);
            activite.setProchaineTentativeAt(null);
        } else if (tentative >= BACKOFF.length) {
            activite.setStatutEnrichissement(StatutEnrichissement.ABANDONNE);
            activite.setProchaineTentativeAt(null);
        } else {
            activite.setProchaineTentativeAt(LocalDateTime.now(clock).plus(BACKOFF[tentative - 1]));
        }

        santeActiviteRepository.save(activite);
    }

    private void abandonner(SanteActivite activite, String message) {
        activite.setStatutEnrichissement(StatutEnrichissement.ABANDONNE);
        activite.setProchaineTentativeAt(null);
        santeActiviteRepository.save(activite);
        log.info("SANTE_ACTIVITE_ABANDON activiteId={} message={}", activite.getId(), message);
    }
}
