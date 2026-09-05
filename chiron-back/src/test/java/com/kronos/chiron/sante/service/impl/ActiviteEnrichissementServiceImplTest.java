package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.StatutEnrichissement;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.sante.persistence.SanteActiviteRepository;
import com.kronos.chiron.sante.service.ActiviteFusionService;
import com.kronos.chiron.sante.service.CaloriesSeanceService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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
    private FitbitService fitbitService;
    @Mock
    private SanteSyncService santeSyncService;
    @Mock
    private ActiviteFusionService activiteFusionService;
    @Mock
    private CaloriesSeanceService caloriesSeanceService;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-19T20:00:00Z"), ZoneId.of("Europe/Paris"));

    private ActiviteEnrichissementServiceImpl service;

    private Utilisateur user;
    private LocalDateTime debut;
    private LocalDateTime fin;

    @BeforeEach
    void setUp() {
        service = new ActiviteEnrichissementServiceImpl(santeActiviteRepository, fitbitService, santeSyncService,
                activiteFusionService, caloriesSeanceService, clock);
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

    // WHY: la séance part vers Google Health à la seconde où elle se termine, et c'est Google
    // qui construit ensuite l'activité autour de l'intervalle qu'on lui a donné. Interroger dans
    // la foulée ne ramenait que l'enveloppe qu'on venait d'écrire.
    @Test
    void planifierEnrichissement_waitsBeforeTheFirstAttempt() {
        // Given
        Seance seance = new Seance();
        seance.setId(42L);
        seance.setUtilisateur(user);
        seance.setStartTime(debut);
        seance.setEndTime(fin);

        // When
        service.planifierEnrichissement(seance);

        // Then
        ArgumentCaptor<SanteActivite> captor = ArgumentCaptor.forClass(SanteActivite.class);
        verify(santeActiviteRepository).save(captor.capture());
        assertThat(captor.getValue().getProchaineTentativeAt())
                .isAfter(LocalDateTime.now(clock));
    }

    @Test
    void enregistrerPousseeGoogle_marksTheRowAsOursAndStoresTheCreatedDataPointId() {
        // Given
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findBySeanceId(42L)).thenReturn(Optional.of(activite));

        // When
        service.enregistrerPousseeGoogle(42L, "users/me/dataTypes/exercise/dataPoints/abc");

        // Then
        assertThat(activite.isPousseGoogle()).isTrue();
        assertThat(activite.getExternalId()).isEqualTo("users/me/dataTypes/exercise/dataPoints/abc");
        verify(santeActiviteRepository).save(activite);
    }

    // WHY: la réponse de création n'est pas garantie de porter un identifiant. Le drapeau, lui,
    // ne dépend que du succès de l'écriture : c'est lui qui autorisera plus tard à recopier les
    // chiffres calculés par Google sur notre intervalle.
    @Test
    void enregistrerPousseeGoogle_googleReturnedNoId_stillMarksTheRowAsOurs() {
        // Given
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findBySeanceId(42L)).thenReturn(Optional.of(activite));

        // When
        service.enregistrerPousseeGoogle(42L, null);

        // Then
        assertThat(activite.isPousseGoogle()).isTrue();
        assertThat(activite.getExternalId()).isNull();
        verify(santeActiviteRepository).save(activite);
    }

    @Test
    void enregistrerPousseeGoogle_alreadyLinked_keepsTheKnownId() {
        // Given
        SanteActivite activite = activiteEnAttente(0);
        activite.setExternalId("deja-connu");
        when(santeActiviteRepository.findBySeanceId(42L)).thenReturn(Optional.of(activite));

        // When
        service.enregistrerPousseeGoogle(42L, "autre");

        // Then
        assertThat(activite.getExternalId()).isEqualTo("deja-connu");
        assertThat(activite.isPousseGoogle()).isTrue();
    }

    @Test
    void tenterEnrichissement_activiteNotFound_isNoOp() {
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.empty());

        service.tenterEnrichissement(10L);

        verify(santeActiviteRepository, never()).save(any());
        verifyNoInteractions(fitbitService, santeSyncService, activiteFusionService);
    }

    @Test
    void tenterEnrichissement_alreadyComplet_isNoOp() {
        SanteActivite activite = activiteEnAttente(0);
        activite.setStatutEnrichissement(StatutEnrichissement.COMPLET);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));

        service.tenterEnrichissement(10L);

        verify(santeActiviteRepository, never()).save(any());
        verifyNoInteractions(fitbitService, santeSyncService, activiteFusionService);
    }

    @Test
    void tenterEnrichissement_notLinked_marksAbandonneImmediately() {
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenThrow(new FitbitService.NotLinkedException());

        service.tenterEnrichissement(10L);

        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.ABANDONNE);
        assertThat(activite.getProchaineTentativeAt()).isNull();
        verifyNoInteractions(santeSyncService, activiteFusionService);
        verify(santeActiviteRepository).save(activite);
    }

    // WHY: un compte non lie ne rendra jamais de frequence cardiaque. Laisser la case vide etait
    // la seule chose a ne pas faire : la duree, les exercices et la morphologie suffisent a en
    // donner l'ordre de grandeur, et le drapeau dit que c'est un calcul, pas une mesure.
    @Test
    void tenterEnrichissement_notLinked_storesTheEstimateAndFlagsIt() {
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenThrow(new FitbitService.NotLinkedException());
        when(caloriesSeanceService.estimer(activite)).thenReturn(324);

        service.tenterEnrichissement(10L);

        assertThat(activite.getCalories()).isEqualTo(324);
        assertThat(activite.isCaloriesEstimees()).isTrue();
        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.ABANDONNE);
    }

    @Test
    void tenterEnrichissement_notLinkedAndNothingToEstimate_leavesCaloriesEmpty() {
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenThrow(new FitbitService.NotLinkedException());
        when(caloriesSeanceService.estimer(activite)).thenReturn(null);

        service.tenterEnrichissement(10L);

        assertThat(activite.getCalories()).isNull();
        assertThat(activite.isCaloriesEstimees()).isFalse();
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
    void tenterEnrichissement_fusionFindsData_marksCompletFromFusedValues() {
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenReturn("token");
        // Simule ActiviteFusionService qui a rempli fcMoyenne et chargeCardio depuis les buckets.
        doAnswer(invocation -> {
            activite.setFcMoyenne(120.0);
            activite.setFcMin(100);
            activite.setFcMax(140);
            activite.setChargeCardio(30.0);
            return null;
        }).when(activiteFusionService).fusionnerActivite(activite);

        service.tenterEnrichissement(10L);

        assertThat(activite.getFcMoyenne()).isEqualTo(120.0);
        assertThat(activite.getChargeCardio()).isEqualTo(30.0);
        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.COMPLET);
        assertThat(activite.getProchaineTentativeAt()).isNull();
        assertThat(activite.getTentativesEnrichissement()).isEqualTo(1);
        verify(activiteFusionService).fusionnerActivite(activite);
    }

    @Test
    void tenterEnrichissement_fcSansChargeCardio_neVerrouillePasEnComplet() {
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenReturn("token");
        // fusionnerActivite pose une FC mais pas de charge cardio (pas de zones calculables) :
        // le verrou ne doit plus se refermer sur la seule FC, sans quoi la ligne se fige
        // avec des calories et une charge cardio à null pour toujours.
        doAnswer(invocation -> {
            activite.setFcMoyenne(68.0);
            return null;
        }).when(activiteFusionService).fusionnerActivite(activite);

        service.tenterEnrichissement(10L);

        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.EN_ATTENTE);
        assertThat(activite.getTentativesEnrichissement()).isEqualTo(1);
    }

    @Test
    void tenterEnrichissement_noDataYetBudgetRemaining_staysEnAttenteWithBackoff() {
        SanteActivite activite = activiteEnAttente(0);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenReturn("token");

        service.tenterEnrichissement(10L);

        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.EN_ATTENTE);
        assertThat(activite.getTentativesEnrichissement()).isEqualTo(1);
        assertThat(activite.getProchaineTentativeAt()).isEqualTo(LocalDateTime.now(clock).plusMinutes(2));
    }

    // WHY: la fréquence cardiaque n'entre dans Google que quand le bracelet se synchronise, des
    // heures après la séance. Le dernier palier de six heures n'était jamais atteint — la borne
    // d'abandon se déclenchait une tentative trop tôt — et la fenêtre se refermait à 2 h 47,
    // avant la seule donnée qu'on attendait.
    @Test
    void tenterEnrichissement_noDataOnTheLastRung_waitsSixHoursRatherThanGivingUp() {
        SanteActivite activite = activiteEnAttente(4);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenReturn("token");

        service.tenterEnrichissement(10L);

        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.EN_ATTENTE);
        assertThat(activite.getTentativesEnrichissement()).isEqualTo(5);
        assertThat(activite.getProchaineTentativeAt()).isEqualTo(LocalDateTime.now(clock).plusHours(6));
    }

    @Test
    void tenterEnrichissement_noDataBudgetExhausted_marksAbandonne() {
        SanteActivite activite = activiteEnAttente(5);
        when(santeActiviteRepository.findById(10L)).thenReturn(Optional.of(activite));
        when(fitbitService.getValidToken("athlete")).thenReturn("token");

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

        service.tenterEnrichissement(10L);

        assertThat(activite.getStatutEnrichissement()).isEqualTo(StatutEnrichissement.EN_ATTENTE);
    }
}
