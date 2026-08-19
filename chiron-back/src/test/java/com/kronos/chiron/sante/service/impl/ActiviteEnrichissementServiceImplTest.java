package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SanteFrequenceCardiaque;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.persistence.SanteFrequenceCardiaqueRepository;
import com.kronos.chiron.sante.service.SanteSyncService;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.utilisateur.model.Utilisateur;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActiviteEnrichissementServiceImplTest {

    @Mock
    private SanteActiviteRepository santeActiviteRepository;
    @Mock
    private SanteFrequenceCardiaqueRepository santeFrequenceCardiaqueRepository;
    @Mock
    private FitbitService fitbitService;
    @Mock
    private SanteSyncService santeSyncService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-19T20:00:00Z"), ZoneId.of("Europe/Paris"));

    private ActiviteEnrichissementServiceImpl service;

    private Utilisateur user;
    private LocalDateTime debut;
    private LocalDateTime fin;

    @BeforeEach
    void setUp() {
        service = new ActiviteEnrichissementServiceImpl(santeActiviteRepository, santeFrequenceCardiaqueRepository,
                fitbitService, santeSyncService, clock);
        user = Utilisateur.builder().id(1L).username("athlete").build();
        debut = LocalDateTime.of(2026, 8, 19, 18, 0);
        fin = LocalDateTime.of(2026, 8, 19, 19, 15);
    }

    private SanteActivite activiteEnAttente(int tentatives) {
        return SanteActivite.builder()
                .id(10L).utilisateur(user).source(SourceActivite.CHIRON_MUSCU)
                .typeActivite(TypeActivite.MUSCULATION)
                .startTime(debut).endTime(fin)
                .statutEnrichissement(StatutEnrichissement.EN_ATTENTE)
                .tentativesEnrichissement(tentatives)
                .build();
    }

    @Test
    void planifierEnrichissement_insertsStubRowEnAttente() {
        Seance seance = new Seance();
        seance.setId(42L);
        seance.setUtilisateur(user);
        seance.setStartTime(debut);
        seance.setEndTime(fin);

        service.planifierEnrichissement(seance);

        ArgumentCaptor<SanteActivite> captor = ArgumentCaptor.forClass(SanteActivite.class);
        verify(santeActiviteRepository).save(captor.capture());
        SanteActivite saved = captor.getValue();
        assertThat(saved.getSeance()).isEqualTo(seance);
        assertThat(saved.getSource()).isEqualTo(SourceActivite.CHIRON_MUSCU);
        assertThat(saved.getTypeActivite()).isEqualTo(TypeActivite.MUSCULATION);
        assertThat(saved.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.EN_ATTENTE);
        assertThat(saved.getStartTime()).isEqualTo(debut);
        assertThat(saved.getEndTime()).isEqualTo(fin);
    }

    @Test
    void tenterEnrichissement_activiteNotFound_isNoOp() {
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.empty());

        service.tenterEnrichissement(10L);

        verify(santeActiviteRepository, never()).save(any());
        verifyNoInteractions(fitbitService, santeSyncService);
    }

    @Test
    void tenterEnrichissement_alreadyComplet_isNoOp() {
        SanteActivite activite = activiteEnAttente(0);
        activite.setStatutEnrichissement(StatutEnrichissement.COMPLET);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));

        service.tenterEnrichissement(10L);

        verify(santeActiviteRepository, never()).save(any());
        verifyNoInteractions(fitbitService, santeSyncService);
    }

    @Test
    void tenterEnrichissement_notLinked_marksAbandonneImmediately() {
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenThrow(new FitbitService.NotLinkedException());

        service.tenterEnrichissement(10L);

        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.ABANDONNE);
        assertThat(activite.getProchaineTentativeAt()).isNull();
        verifyNoInteractions(santeSyncService);
        verify(santeActiviteRepository).save(activite);
    }

    @Test
    void tenterEnrichissement_expiredToken_marksAbandonneImmediately() {
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenThrow(new FitbitService.ExpiredException());

        service.tenterEnrichissement(10L);

        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.ABANDONNE);
    }

    @Test
    void tenterEnrichissement_heartRateDataFound_marksCompletWithAveragedValues() {
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenReturn("token");

        List<SanteFrequenceCardiaque> buckets = List.of(
                SanteFrequenceCardiaque.builder().fcMin(100).fcMoyenne(110.0).fcMax(120).build(),
                SanteFrequenceCardiaque.builder().fcMin(120).fcMoyenne(130.0).fcMax(140).build());
        when(santeFrequenceCardiaqueRepository
                .findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user, debut, fin))
                .thenReturn(buckets);

        service.tenterEnrichissement(10L);

        assertThat(activite.getFcMoyenne()).isEqualTo(120.0);
        assertThat(activite.getFcMin()).isEqualTo(100);
        assertThat(activite.getFcMax()).isEqualTo(140);
        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.COMPLET);
        assertThat(activite.getProchaineTentativeAt()).isNull();
        assertThat(activite.getTentativesEnrichissement()).isEqualTo(1);
    }

    @Test
    void tenterEnrichissement_noDataYetBudgetRemaining_staysEnAttenteWithBackoff() {
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenReturn("token");
        when(santeFrequenceCardiaqueRepository
                .findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user, debut, fin))
                .thenReturn(List.of());

        service.tenterEnrichissement(10L);

        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.EN_ATTENTE);
        assertThat(activite.getTentativesEnrichissement()).isEqualTo(1);
        assertThat(activite.getProchaineTentativeAt()).isEqualTo(LocalDateTime.now(clock).plusMinutes(2));
    }

    @Test
    void tenterEnrichissement_noDataBudgetExhausted_marksAbandonne() {
        SanteActivite activite = activiteEnAttente(4);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenReturn("token");
        when(santeFrequenceCardiaqueRepository
                .findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user, debut, fin))
                .thenReturn(List.of());

        service.tenterEnrichissement(10L);

        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.ABANDONNE);
        assertThat(activite.getProchaineTentativeAt()).isNull();
    }

    @Test
    void tenterEnrichissement_syncRecentThrows_doesNotPropagate() {
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenReturn("token");
        doThrow(new RuntimeException("boom")).when(santeSyncService).syncRecent("athlete", 1);
        when(santeFrequenceCardiaqueRepository
                .findByUtilisateurAndHorodatageBetweenOrderByHorodatageAsc(user, debut, fin))
                .thenReturn(List.of());

        service.tenterEnrichissement(10L);

        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.EN_ATTENTE);
    }
}
