package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.dto.SeuilsCardiaquesDto;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SanteFrequenceCardiaque;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.model.ZoneCardiaque;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.persistence.SanteFrequenceCardiaqueRepository;
import com.kronos.chiron.sante.service.ActiviteFusionService;
import com.kronos.chiron.sante.service.CaloriesEffortService;
import com.kronos.chiron.sante.service.SeuilsCardiaquesService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class ActiviteFusionServiceImpl implements ActiviteFusionService {

    // WHY: même tolérance que SanteSyncServiceImpl.enregistrerActiviteGoogle — l'horodatage
    // Google et celui de Chiron divergent de quelques secondes à quelques minutes.
    private static final int TOLERANCE_CHEVAUCHEMENT_MINUTES = 5;
    private static final int MINUTES_PAR_BUCKET = 5;

    private final SanteActiviteRepository santeActiviteRepository;
    private final SanteFrequenceCardiaqueRepository santeFrequenceCardiaqueRepository;
    private final CaloriesEffortService caloriesEffortService;
    private final SeuilsCardiaquesService seuilsCardiaquesService;
    private final Clock clock;

    @Override
    @Transactional
    public void fusionnerFenetre(Utilisateur utilisateur, int joursFenetre) {
        LocalDateTime to = LocalDateTime.now(clock);
        LocalDateTime from = to.minusDays(joursFenetre);
        List<SanteActivite> chironActivites = santeActiviteRepository
                .findByUtilisateurAndSourceAndStartTimeBetweenOrderByStartTimeAsc(utilisateur,
                        SourceActivite.CHIRON_MUSCU, from, to);
        for (SanteActivite chiron : chironActivites) {
            fusionnerActivite(chiron);
        }
    }

    // WHY: appelée à la fois en rattrapage périodique et au moment où un exercice Google
    // vient d'être ingéré pour une séance Chiron déjà connue — dans les deux cas, une ligne
    // GOOGLE_DETECTE contenue dans la fenêtre Chiron ne doit jamais survivre en doublon, et
    // les agrégats de la ligne Chiron doivent toujours être calculés sur SA fenêtre à elle,
    // jamais recopiés depuis la fenêtre, plus courte, de l'exercice détecté par Google.
    @Override
    @Transactional
    public void fusionnerActivite(SanteActivite chiron) {
        if (chiron.getEndTime() == null) return;
        LocalDateTime debut = chiron.getStartTime().minusMinutes(TOLERANCE_CHEVAUCHEMENT_MINUTES);
        LocalDateTime fin = chiron.getEndTime().plusMinutes(TOLERANCE_CHEVAUCHEMENT_MINUTES);

        List<SanteActivite> googleContenues = santeActiviteRepository
                .findByUtilisateurAndSourceAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                        chiron.getUtilisateur(), SourceActivite.GOOGLE_DETECTE, debut, fin);

        if (!googleContenues.isEmpty()) {
            if (chiron.getExternalId() == null) {
                googleContenues.stream().map(SanteActivite::getExternalId).filter(Objects::nonNull).findFirst()
                        .ifPresent(chiron::setExternalId);
            }
            santeActiviteRepository.deleteAll(googleContenues);
            log.info("SANTE_ACTIVITE_FUSION activiteId={} absorbees={}", chiron.getId(), googleContenues.size());
        }

        recalculer(chiron);
    }

    private void recalculer(SanteActivite activite) {
        List<SanteFrequenceCardiaque> buckets = santeFrequenceCardiaqueRepository
                .findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(activite.getUtilisateur(),
                        activite.getStartTime(), activite.getEndTime());
        if (buckets.isEmpty()) return;

        appliquerFrequenceEtZones(activite, buckets);
        activite.setCalories(caloriesEffortService.estimer(activite.getUtilisateur(), buckets));
        activite.setStatutEnrichissement(StatutEnrichissement.COMPLET);
        activite.setProchaineTentativeAt(null);
        santeActiviteRepository.save(activite);
    }

    // WHY: moyenne pondérée par nb_echantillons — un bucket de 5 minutes couvert par 60
    // mesures ne doit pas peser autant qu'un bucket couvert par 3 ; l'ancienne moyenne de
    // moyennes noyait une séance intense dans les buckets creux (68 bpm sur 46 minutes).
    private void appliquerFrequenceEtZones(SanteActivite activite, List<SanteFrequenceCardiaque> buckets) {
        SeuilsCardiaquesDto seuils = seuilsCardiaquesService.calculer(activite.getUtilisateur());

        double sommePonderee = 0;
        int poidsTotal = 0;
        Integer min = null;
        Integer max = null;
        int minutesBasse = 0;
        int minutesBruleuse = 0;
        int minutesCardio = 0;
        int minutesPic = 0;

        for (SanteFrequenceCardiaque bucket : buckets) {
            if (bucket.getFcMoyenne() != null) {
                int poids = bucket.getNbEchantillons() != null && bucket.getNbEchantillons() > 0
                        ? bucket.getNbEchantillons()
                        : 1;
                sommePonderee += bucket.getFcMoyenne() * poids;
                poidsTotal += poids;

                ZoneCardiaque zone = ZoneCardiaque.fromBpm((int) Math.round(bucket.getFcMoyenne()), seuils);
                switch (zone) {
                    case HORS_ZONE -> minutesBasse += MINUTES_PAR_BUCKET;
                    case BRULE_GRAISSE -> minutesBruleuse += MINUTES_PAR_BUCKET;
                    case CARDIO -> minutesCardio += MINUTES_PAR_BUCKET;
                    case PIC -> minutesPic += MINUTES_PAR_BUCKET;
                }
            }
            if (bucket.getFcMin() != null) {
                min = min == null ? bucket.getFcMin() : Math.min(min, bucket.getFcMin());
            }
            if (bucket.getFcMax() != null) {
                max = max == null ? bucket.getFcMax() : Math.max(max, bucket.getFcMax());
            }
        }

        if (poidsTotal > 0) activite.setFcMoyenne(sommePonderee / poidsTotal);
        if (min != null) activite.setFcMin(min);
        if (max != null) activite.setFcMax(max);
        activite.setMinutesZoneBasse(minutesBasse);
        activite.setMinutesZoneBruleuse(minutesBruleuse);
        activite.setMinutesZoneCardio(minutesCardio);
        activite.setMinutesZonePic(minutesPic);
        activite.setMinutesZoneActive(minutesBruleuse + minutesCardio + minutesPic);
        activite.setChargeCardio(ZoneCardiaque.chargeCardio(minutesBruleuse, minutesCardio, minutesPic));
    }
}
