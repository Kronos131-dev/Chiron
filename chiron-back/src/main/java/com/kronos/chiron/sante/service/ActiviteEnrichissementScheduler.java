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

    // WHY: une relance de masse — V60 a remis toute une historique en attente — passerait sinon
    // en un seul tour, et chaque activite d'un compte lie declenche une synchronisation Google.
    // Le lot borne etale le rattrapage sur plusieurs passages plutot que de marteler l'API.
    private static final int LOT_MAX = 20;

    private final SanteActiviteRepository santeActiviteRepository;
    private final ActiviteEnrichissementService activiteEnrichissementService;
    private final Clock clock;

    @Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    public void relancerEnrichissementsEnAttente() {
        List<SanteActivite> enAttente = santeActiviteRepository
                .findByStatutEnrichissementAndProchaineTentativeAtLessThanEqual(StatutEnrichissement.EN_ATTENTE,
                        LocalDateTime.now(clock));
        List<SanteActivite> lot = enAttente.stream().limit(LOT_MAX).toList();
        for (SanteActivite activite : lot) {
            activiteEnrichissementService.tenterEnrichissement(activite.getId());
        }
        if (!lot.isEmpty()) {
            log.info("SANTE_ACTIVITE_RELANCE activites={} enAttente={}", lot.size(), enAttente.size());
        }
    }
}
