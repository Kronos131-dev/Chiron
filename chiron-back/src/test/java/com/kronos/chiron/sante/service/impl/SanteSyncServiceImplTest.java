package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.fitbit.client.FitbitClient;
import com.kronos.chiron.fitbit.client.GoogleHealthDataType;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.model.SanteSyncState;
import com.kronos.chiron.sante.model.StatutSync;
import com.kronos.chiron.sante.persistence.SanteFrequenceCardiaqueRepository;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.sante.persistence.SanteSyncStateRepository;
import com.kronos.chiron.sante.service.ChargeCardioService;
import com.kronos.chiron.sante.service.ScoreSommeilService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SanteSyncServiceImplTest {

    @Mock
    private FitbitService fitbitService;
    @Mock
    private FitbitClient fitbitClient;
    @Mock
    private UtilisateurRepository utilisateurRepository;
    @Mock
    private SanteJourRepository santeJourRepository;
    @Mock
    private SanteSommeilRepository santeSommeilRepository;
    @Mock
    private SanteFrequenceCardiaqueRepository santeFrequenceCardiaqueRepository;
    @Mock
    private SanteSyncStateRepository santeSyncStateRepository;
    @Mock
    private ScoreSommeilService scoreSommeilService;
    @Mock
    private ChargeCardioService chargeCardioService;

    @Spy
    private Clock clock = Clock.fixed(java.time.Instant.parse("2026-08-17T10:00:00Z"), ZoneId.of("Europe/Paris"));

    @InjectMocks
    private SanteSyncServiceImpl santeSyncService;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().id(1L).username("athlete").build();
        when(utilisateurRepository.findByUsername("athlete")).thenReturn(Optional.of(user));
        when(santeSyncStateRepository.findByUtilisateurAndTypeDonnee(eq(user), any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void syncRecent_notLinked_touchesNothing() {
        when(fitbitService.getValidToken("athlete")).thenThrow(new FitbitService.NotLinkedException());

        santeSyncService.syncRecent("athlete", 3);

        verifyNoInteractions(santeJourRepository, santeSommeilRepository, santeFrequenceCardiaqueRepository,
                santeSyncStateRepository);
    }

    @Test
    void syncRecent_expired_touchesNothing() {
        when(fitbitService.getValidToken("athlete")).thenThrow(new FitbitService.ExpiredException());

        santeSyncService.syncRecent("athlete", 3);

        verifyNoInteractions(santeJourRepository, santeSommeilRepository, santeFrequenceCardiaqueRepository,
                santeSyncStateRepository);
    }

    @Test
    void syncRecent_unknownUser_doesNotThrow() {
        when(utilisateurRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        santeSyncService.syncRecent("ghost", 3);

        verifyNoInteractions(fitbitService, santeJourRepository);
    }

    @Test
    void syncRecent_emptyResponses_marksEveryDataTypeAsSuccessful() {
        when(fitbitService.getValidToken("athlete")).thenReturn("token");

        santeSyncService.syncRecent("athlete", 1);

        ArgumentCaptor<SanteSyncState> captor = ArgumentCaptor.forClass(SanteSyncState.class);
        verify(santeSyncStateRepository, times(GoogleHealthDataType.values().length)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(e -> assertThat(e.getDernierStatut()).isEqualTo(StatutSync.OK));
        verify(chargeCardioService).recalculerPlage(eq(user), any(), any());
        verify(scoreSommeilService).recalculerPlage(eq(user), any(), any());
    }

    @Test
    void syncRecent_stepsCallFails_othersStillSucceed() {
        when(fitbitService.getValidToken("athlete")).thenReturn("token");
        when(fitbitClient.dailyRollUp(eq("token"), eq(GoogleHealthDataType.STEPS), any(), any()))
                .thenThrow(new RuntimeException("Google Health a renvoyé 500 INTERNAL_SERVER_ERROR"));

        santeSyncService.syncRecent("athlete", 1);

        ArgumentCaptor<SanteSyncState> captor = ArgumentCaptor.forClass(SanteSyncState.class);
        verify(santeSyncStateRepository, times(GoogleHealthDataType.values().length)).save(captor.capture());
        List<SanteSyncState> saved = captor.getAllValues();
        assertThat(saved).filteredOn(e -> e.getTypeDonnee().equals(GoogleHealthDataType.STEPS.name()))
                .allSatisfy(e -> assertThat(e.getDernierStatut()).isEqualTo(StatutSync.INDISPONIBLE));
        assertThat(saved).filteredOn(e -> e.getTypeDonnee().equals(GoogleHealthDataType.DISTANCE.name()))
                .allSatisfy(e -> assertThat(e.getDernierStatut()).isEqualTo(StatutSync.OK));
    }

    @Test
    void syncRecent_unauthorizedError_marksNonAutorise() {
        when(fitbitService.getValidToken("athlete")).thenReturn("token");
        when(fitbitClient.dailyRollUp(eq("token"), eq(GoogleHealthDataType.STEPS), any(), any()))
                .thenThrow(new FitbitClient.FitbitUnauthorizedException("Token Google Health rejeté (401)"));

        santeSyncService.syncRecent("athlete", 1);

        ArgumentCaptor<SanteSyncState> captor = ArgumentCaptor.forClass(SanteSyncState.class);
        verify(santeSyncStateRepository, times(GoogleHealthDataType.values().length)).save(captor.capture());
        assertThat(captor.getAllValues())
                .filteredOn(e -> e.getTypeDonnee().equals(GoogleHealthDataType.STEPS.name()))
                .allSatisfy(e -> assertThat(e.getDernierStatut()).isEqualTo(StatutSync.NON_AUTORISE));
    }

    @Test
    void ensureBackfillAsync_everyTypeAlreadyBackfilled_doesNotResync() {
        // Given
        when(santeSyncStateRepository.findByUtilisateur(user)).thenReturn(
                java.util.Arrays.stream(GoogleHealthDataType.values())
                        .map(type -> SanteSyncState.builder().utilisateur(user).typeDonnee(type.name())
                                .backfillTermine(true).build())
                        .toList());

        // When
        santeSyncService.ensureBackfillAsync("athlete");

        // Then
        verifyNoInteractions(fitbitService);
    }

    @Test
    void ensureBackfillAsync_oneTypeStillMissingItsBackfill_resyncs() {
        // Given
        List<SanteSyncState> etats = java.util.Arrays.stream(GoogleHealthDataType.values())
                .map(type -> SanteSyncState.builder().utilisateur(user).typeDonnee(type.name())
                        .backfillTermine(type != GoogleHealthDataType.DAILY_VO2_MAX).build())
                .toList();
        when(santeSyncStateRepository.findByUtilisateur(user)).thenReturn(etats);
        when(fitbitService.getValidToken("athlete")).thenReturn("token");

        // When
        santeSyncService.ensureBackfillAsync("athlete");

        // Then
        verify(fitbitService).getValidToken("athlete");
    }

    @Test
    void ensureBackfillAsync_firstTime_runsFullBackfill() {
        when(santeSyncStateRepository.findByUtilisateur(user)).thenReturn(List.of());
        when(fitbitService.getValidToken("athlete")).thenReturn("token");

        santeSyncService.ensureBackfillAsync("athlete");

        verify(fitbitService).getValidToken("athlete");
        verify(chargeCardioService).recalculerPlage(eq(user), any(), any());
    }
}
