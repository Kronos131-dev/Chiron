package com.kronos.chiron.fitbit.service.impl;

import com.kronos.chiron.fitbit.client.FitbitClient;
import com.kronos.chiron.fitbit.client.GoogleHealthParser;
import com.kronos.chiron.fitbit.service.FitbitPushService;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.service.ActiviteEnrichissementService;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.seance.service.SeanceResumeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class FitbitPushServiceImpl implements FitbitPushService {

    private final SeanceRepository seanceRepository;
    private final SeanceResumeService seanceResumeService;
    private final FitbitService fitbitService;
    private final FitbitClient fitbitClient;
    private final ActiviteEnrichissementService activiteEnrichissementService;
    private final Clock clock;

    // WHY: la valeur exacte du type d'exercice pour la musculation dans Google Health API
    // n'a pas été confirmée (doc Google tronque avant listing complet des ExerciseType).
    // À vérifier contre google.devicesandservices.health.v4.Exercise.ExerciseType.
    private static final String EXERCISE_TYPE_MUSCULATION = "WEIGHT_TRAINING";

    @Override
    @Async
    public void pousserSeance(Long seanceId) {
        if (seanceId == null) return;

        Seance seance = seanceRepository.findById(seanceId).orElse(null);
        if (seance == null || seance.getUtilisateur() == null) return;
        if (seance.getStartTime() == null || seance.getEndTime() == null) return;
        if (seance.getExercices() == null || seance.getExercices().isEmpty()) return;

        String username = seance.getUtilisateur().getUsername();
        String notes = seanceResumeService.decrireContenu(seanceId);
        if (notes == null || notes.isBlank()) return;

        try {
            String accessToken = fitbitService.getValidToken(username);

            ZoneId zone = clock.getZone();
            ZonedDateTime startZdt = seance.getStartTime().atZone(zone);
            ZonedDateTime endZdt = seance.getEndTime().atZone(zone);
            Instant startUtc = startZdt.toInstant();
            Instant endUtc = endZdt.toInstant();
            String startUtcOffset = formatOffset(startZdt.getOffset());
            String endUtcOffset = formatOffset(endZdt.getOffset());

            String titre = seance.getTitre() != null && !seance.getTitre().isBlank()
                    ? seance.getTitre()
                    : "Séance d'entraînement";

            JsonNode creation = fitbitClient.pousserSeance(accessToken, startUtc, startUtcOffset, endUtc,
                    endUtcOffset, EXERCISE_TYPE_MUSCULATION, titre, notes);

            // WHY: l'identifiant du point créé est ce qui permettra de reconnaître, dans la
            // prochaine synchronisation, l'activité que Google a calculée autour de NOTRE
            // intervalle — et donc de recopier ses chiffres sans les confondre avec ceux d'un
            // exercice que la montre aurait détecté toute seule sur une fenêtre plus courte.
            activiteEnrichissementService.enregistrerPousseeGoogle(seanceId,
                    GoogleHealthParser.dataPointName(creation));
        } catch (FitbitService.NotLinkedException | FitbitService.ExpiredException e) {
        } catch (RuntimeException e) {
            log.warn("FITBIT_SEANCE_PUSH_FAILED user={} seanceId={} : {}", username, seanceId,
                    e.getMessage());
        }
    }

    private static String formatOffset(ZoneOffset offset) {
        return offset.getTotalSeconds() + "s";
    }
}
