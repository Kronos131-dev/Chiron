package com.kronos.chiron.fitbit.service.impl;

import com.kronos.chiron.fitbit.client.FitbitClient;
import com.kronos.chiron.fitbit.service.FitbitService;

import org.mockito.Spy;

import java.time.ZoneId;

import java.time.Clock;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.journalier.service.RecoveryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FitbitSyncServiceTest {

    private final JsonMapper json = new JsonMapper();

    @Mock
    private FitbitService fitbitService;
    @Mock
    private FitbitClient fitbitClient;
    @Mock
    private RecoveryService recoveryService;
    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Spy
    private Clock clock = Clock.system(ZoneId.of("Europe/Paris"));

    @InjectMocks
    private FitbitSyncServiceImpl syncService;

    @Test
    void syncEtatJournalier_swallowsFitbitErrors() {
        Utilisateur user = Utilisateur.builder().id(1L).username("athlete").build();
        when(utilisateurRepository.findByUsername("athlete")).thenReturn(Optional.of(user));
        when(fitbitService.getValidToken("athlete")).thenReturn("tok");
        when(fitbitClient.listSleep(eq("tok"), any()))
                .thenThrow(new FitbitClient.FitbitUnavailableException("down"));

        assertThatCode(() -> syncService.syncEtatJournalier("athlete", 7))
                .doesNotThrowAnyException();
        verify(recoveryService, never()).upsertFromFitbit(any(), any(), any());
    }

    @Test
    void syncEtatJournalier_notLinked_isSilent() {
        Utilisateur user = Utilisateur.builder().id(1L).username("athlete").build();
        when(utilisateurRepository.findByUsername("athlete")).thenReturn(Optional.of(user));
        when(fitbitService.getValidToken("athlete"))
                .thenThrow(new FitbitService.NotLinkedException());

        assertThatCode(() -> syncService.syncEtatJournalier("athlete", 7))
                .doesNotThrowAnyException();
        verify(recoveryService, never()).upsertFromFitbit(any(), any(), any());
    }

    @Test
    void syncEtatJournalier_upsertsParsedSleep() {
        Utilisateur user = Utilisateur.builder().id(1L).username("athlete").build();
        when(utilisateurRepository.findByUsername("athlete")).thenReturn(Optional.of(user));
        when(fitbitService.getValidToken("athlete")).thenReturn("tok");
        when(fitbitClient.listSleep(eq("tok"), any())).thenReturn(json.readTree("""
                {"dataPoints":[
                  {"sleep":{"interval":{"endTime":"2026-05-20T07:00:00Z"},"summary":{"minutesAsleep":480}}}
                ]}"""));
        when(recoveryService.upsertFromFitbit(eq(user), eq(LocalDate.of(2026, 5, 20)), eq(8.0)))
                .thenReturn(true);

        syncService.syncEtatJournalier("athlete", 7);

        verify(recoveryService).upsertFromFitbit(user, LocalDate.of(2026, 5, 20), 8.0);
    }
}
