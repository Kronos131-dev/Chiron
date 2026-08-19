package com.kronos.chiron.sante.service;

import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActiviteEnrichissementScheduler {

    private final SanteActiviteRepository santeActiviteRepository;
    private final ActiviteEnrichissementService activiteEnrichissementService;
    private final Clock clock;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void relancerEnrichissementsEnAttente() {
        List<SanteActivite> enAttente = santeActiviteRepository
                .findByStatutEnrichissementAndProchaineTentativeAtLessThanEqual(StatutEnrichissement.EN_ATTENTE,
                        LocalDateTime.now(clock));
        for (SanteActivite activite : enAttente) {
            activiteEnrichissementService.tenterEnrichissement(activite.getId());
        }
        if (!enAttente.isEmpty()) {
            log.info("SANTE_ACTIVITE_RELANCE activites={}", enAttente.size());
        }
    }
}
