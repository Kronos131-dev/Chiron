package com.kronos.chiron.fitbit.service.impl;

import com.kronos.chiron.fitbit.client.FitbitClient;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.service.ActiviteEnrichissementService;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.seance.service.SeanceResumeService;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FitbitPushServiceImplTest {

    @Mock
    private SeanceRepository seanceRepository;
    @Mock
    private SeanceResumeService seanceResumeService;
    @Mock
    private FitbitService fitbitService;
    @Mock
    private FitbitClient fitbitClient;
    @Mock
    private ActiviteEnrichissementService activiteEnrichissementService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-17T10:00:00Z"), ZoneId.of("Europe/Paris"));

    @InjectMocks
    private FitbitPushServiceImpl fitbitPushService;

    private final JsonMapper json = new JsonMapper();

    @BeforeEach
    void setUp() {
        Seance seance = new Seance();
        seance.setId(42L);
        seance.setTitre("Push Day");
        seance.setUtilisateur(Utilisateur.builder().id(1L).username("athlete").build());
        seance.setStartTime(LocalDateTime.of(2026, 8, 17, 18, 0));
        seance.setEndTime(LocalDateTime.of(2026, 8, 17, 19, 15));
        Exercice exo = new Exercice();
        exo.setNom("Développé couché");
        seance.addExercice(exo);

        when(seanceRepository.findById(42L)).thenReturn(Optional.of(seance));
        when(seanceResumeService.decrireContenu(42L)).thenReturn("Développé couché : 4×8");
        when(fitbitService.getValidToken("athlete")).thenReturn("token");
    }

    // WHY: l'identifiant du point créé est le seul lien sûr entre la séance qu'on pousse et
    // l'activité que Google calcule ensuite autour de notre intervalle. Sans lui, la prochaine
    // synchronisation ne saurait pas distinguer notre séance d'un exercice que la montre aurait
    // détecté toute seule sur une fenêtre plus courte.
    @Test
    void pousserSeance_recordsTheIdentifierGoogleReturned() {
        // Given
        when(fitbitClient.pousserSeance(anyString(), any(), anyString(), any(), anyString(), anyString(),
                anyString(), anyString()))
                .thenReturn(json.readTree("{\"name\":\"users/me/dataTypes/exercise/dataPoints/7\"}"));

        // When
        fitbitPushService.pousserSeance(42L);

        // Then
        verify(activiteEnrichissementService).enregistrerPousseeGoogle(42L,
                "users/me/dataTypes/exercise/dataPoints/7");
    }

    @Test
    void pousserSeance_googleRefusesTheWrite_recordsNothing() {
        // Given
        when(fitbitClient.pousserSeance(anyString(), any(), anyString(), any(), anyString(), anyString(),
                anyString(), anyString()))
                .thenThrow(new FitbitClient.FitbitUnavailableException("Accès Google Health refusé (403)"));

        // When
        fitbitPushService.pousserSeance(42L);

        // Then
        verify(activiteEnrichissementService, never()).enregistrerPousseeGoogle(any(), any());
    }

    @Test
    void pousserSeance_accountNotLinked_recordsNothing() {
        // Given
        when(fitbitService.getValidToken("athlete"))
                .thenThrow(new FitbitService.NotLinkedException());

        // When
        fitbitPushService.pousserSeance(42L);

        // Then
        verify(fitbitClient, never()).pousserSeance(anyString(), any(), anyString(), any(), anyString(),
                anyString(), anyString(), anyString());
        verify(activiteEnrichissementService, never()).enregistrerPousseeGoogle(eq(42L), any());
    }
}
