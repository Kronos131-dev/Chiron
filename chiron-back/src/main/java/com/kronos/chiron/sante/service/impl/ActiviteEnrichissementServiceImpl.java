package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SanteFrequenceCardiaque;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.persistence.SanteFrequenceCardiaqueRepository;
import com.kronos.chiron.sante.service.ActiviteEnrichissementService;
import com.kronos.chiron.sante.service.SanteSyncService;
import com.kronos.chiron.seance.model.Seance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActiviteEnrichissementServiceImpl implements ActiviteEnrichissementService {

    private static final Duration[] BACKOFF = {Duration.ofMinutes(2), Duration.ofMinutes(10),
            Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6)};

    private final SanteActiviteRepository santeActiviteRepository;
    private final SanteFrequenceCardiaqueRepository santeFrequenceCardiaqueRepository;
    private final FitbitService fitbitService;
    private final SanteSyncService santeSyncService;
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

        List<SanteFrequenceCardiaque> buckets = santeFrequenceCardiaqueRepository
                .findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(activite.getUtilisateur(),
                        activite.getStartTime(), activite.getEndTime());
        if (!buckets.isEmpty()) {
            appliquerFrequenceCardiaque(activite, buckets);
        }

        boolean donneesTrouvees = activite.getFcMoyenne() != null;
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

    private void appliquerFrequenceCardiaque(SanteActivite activite, List<SanteFrequenceCardiaque> buckets) {
        List<Double> moyennes = buckets.stream().map(SanteFrequenceCardiaque::getFcMoyenne)
                .filter(Objects::nonNull).toList();
        List<Integer> mins = buckets.stream().map(SanteFrequenceCardiaque::getFcMin)
                .filter(Objects::nonNull).toList();
        List<Integer> maxs = buckets.stream().map(SanteFrequenceCardiaque::getFcMax)
                .filter(Objects::nonNull).toList();

        if (!moyennes.isEmpty()) {
            activite.setFcMoyenne(moyennes.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        }
        if (!mins.isEmpty()) {
            activite.setFcMin(mins.stream().mapToInt(Integer::intValue).min().orElse(0));
        }
        if (!maxs.isEmpty()) {
            activite.setFcMax(maxs.stream().mapToInt(Integer::intValue).max().orElse(0));
        }
    }

    private void abandonner(SanteActivite activite, String message) {
        activite.setStatutEnrichissement(StatutEnrichissement.ABANDONNE);
        activite.setProchaineTentativeAt(null);
        santeActiviteRepository.save(activite);
        log.info("SANTE_ACTIVITE_ABANDON activiteId={} message={}", activite.getId(), message);
    }
}
