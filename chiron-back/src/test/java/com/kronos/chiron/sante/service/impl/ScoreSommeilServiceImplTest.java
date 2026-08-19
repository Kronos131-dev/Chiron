package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.model.SanteSommeil;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.sante.persistence.SanteSommeilRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ScoreSommeilServiceImplTest {

    @Mock
    private SanteSommeilRepository santeSommeilRepository;
    @Mock
    private SanteJourRepository santeJourRepository;

    @InjectMocks
    private ScoreSommeilServiceImpl scoreSommeilService;

    private Utilisateur user;
    private LocalDate date;
    private LocalDate from;
    private LocalDate to;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().id(1L).username("athlete").build();
        date = LocalDate.of(2026, 8, 10);
        from = date;
        to = date;
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(any(), any(), any()))
                .thenReturn(List.of());
    }

    private SanteSommeil.SanteSommeilBuilder baseSession() {
        return SanteSommeil.builder().utilisateur(user).date(date).minutesEndormi(480);
    }

    @Test
    void recalculerPlage_perfectNight_scoresMaximumOnEveryComponent() {
        SanteSommeil session = baseSession()
                .stadesDisponibles(true).minutesProfond(110).minutesParadoxal(130)
                .minutesEveille(0).minutesAgite(0).nbReveils(0)
                .fcSommeilMoyenne(55.0)
                .build();
        when(santeSommeilRepository.findByUtilisateurAndDateBetweenOrderByDebutAsc(user, from, to))
                .thenReturn(List.of(session));

        SanteJour jourCourant = SanteJour.builder().utilisateur(user).date(date).fcRepos(65).vfcMs(60.0).build();
        when(santeJourRepository.findByUtilisateurAndDate(user, date)).thenReturn(Optional.of(jourCourant));
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(user, date.minusDays(30), date))
                .thenReturn(List.of(
                        SanteJour.builder().vfcMs(38.0).build(),
                        SanteJour.builder().vfcMs(39.0).build(),
                        SanteJour.builder().vfcMs(40.0).build(),
                        SanteJour.builder().vfcMs(41.0).build(),
                        SanteJour.builder().vfcMs(42.0).build()));

        scoreSommeilService.recalculerPlage(user, from, to);

        assertThat(session.getScoreDuree()).isEqualTo(50);
        assertThat(session.getScoreComposition()).isEqualTo(25);
        assertThat(session.getScoreRestauration()).isEqualTo(25);
        assertThat(session.getScore()).isEqualTo(100);
    }

    @Test
    void recalculerPlage_noRestlessStageFromGoogle_doesNotAwardFullAgitation() {
        // Given : Google ne publie pas de stade agité, et la nuit compte 45 min d'éveil
        // pour 6 réveils — l'ancien calcul, en lisant minutesAgite nul comme zéro, aurait
        // crédité une nuit parfaitement calme.
        SanteSommeil session = baseSession().stadesDisponibles(true)
                .minutesProfond(100).minutesParadoxal(92)
                .minutesEveille(45).minutesAgite(null).nbReveils(6)
                .fcSommeilMoyenne(55.0)
                .build();
        when(santeSommeilRepository.findByUtilisateurAndDateBetweenOrderByDebutAsc(user, from, to))
                .thenReturn(List.of(session));
        when(santeJourRepository.findByUtilisateurAndDate(user, date)).thenReturn(Optional.empty());

        // When
        scoreSommeilService.recalculerPlage(user, from, to);

        // Then
        assertThat(session.getScoreRestauration()).isLessThan(25);
        assertThat(session.getScore()).isLessThan(100);
    }

    @Test
    void recalculerPlage_siesteSession_isNotScored() {
        // Given
        SanteSommeil sieste = baseSession().sieste(true).stadesDisponibles(true)
                .minutesProfond(100).minutesParadoxal(92).build();
        when(santeSommeilRepository.findByUtilisateurAndDateBetweenOrderByDebutAsc(user, from, to))
                .thenReturn(List.of(sieste));

        // When
        scoreSommeilService.recalculerPlage(user, from, to);

        // Then
        assertThat(sieste.getScore()).isNull();
        assertThat(sieste.getScoreDuree()).isNull();
    }

    @Test
    void recalculerPlage_stagesUnavailable_leavesCompositionNull() {
        SanteSommeil session = baseSession().stadesDisponibles(false).build();
        when(santeSommeilRepository.findByUtilisateurAndDateBetweenOrderByDebutAsc(user, from, to))
                .thenReturn(List.of(session));
        when(santeJourRepository.findByUtilisateurAndDate(user, date)).thenReturn(Optional.empty());

        scoreSommeilService.recalculerPlage(user, from, to);

        assertThat(session.getScoreComposition()).isNull();
        assertThat(session.getScoreDuree()).isEqualTo(50);
    }

    @Test
    void recalculerPlage_zeroMinutesEndormi_skipsSession() {
        SanteSommeil session = SanteSommeil.builder().utilisateur(user).date(date).minutesEndormi(0).build();
        when(santeSommeilRepository.findByUtilisateurAndDateBetweenOrderByDebutAsc(user, from, to))
                .thenReturn(List.of(session));

        scoreSommeilService.recalculerPlage(user, from, to);

        assertThat(session.getScore()).isNull();
    }

    @Test
    void recalculerPlage_nullMinutesEndormi_skipsSession() {
        SanteSommeil session = SanteSommeil.builder().utilisateur(user).date(date).build();
        when(santeSommeilRepository.findByUtilisateurAndDateBetweenOrderByDebutAsc(user, from, to))
                .thenReturn(List.of(session));

        scoreSommeilService.recalculerPlage(user, from, to);

        assertThat(session.getScore()).isNull();
    }

    @Test
    void recalculerPlage_missingRestaurationData_appliesNeutralCredit() {
        SanteSommeil session = baseSession().stadesDisponibles(false).build();
        when(santeSommeilRepository.findByUtilisateurAndDateBetweenOrderByDebutAsc(user, from, to))
                .thenReturn(List.of(session));
        when(santeJourRepository.findByUtilisateurAndDate(user, date)).thenReturn(Optional.empty());

        scoreSommeilService.recalculerPlage(user, from, to);

        assertThat(session.getScoreRestauration()).isEqualTo(11);
        assertThat(session.getScore()).isEqualTo(61);
    }

    @Test
    void recalculerPlage_lessThanFiveDaysOfVfcHistory_appliesNeutralCreditForVfcSubScore() {
        SanteSommeil session = baseSession().stadesDisponibles(false).fcSommeilMoyenne(55.0).build();
        when(santeSommeilRepository.findByUtilisateurAndDateBetweenOrderByDebutAsc(user, from, to))
                .thenReturn(List.of(session));
        SanteJour jourCourant = SanteJour.builder().utilisateur(user).date(date).fcRepos(65).vfcMs(60.0).build();
        when(santeJourRepository.findByUtilisateurAndDate(user, date)).thenReturn(Optional.of(jourCourant));
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(user, date.minusDays(30), date))
                .thenReturn(List.of(SanteJour.builder().vfcMs(40.0).build()));

        scoreSommeilService.recalculerPlage(user, from, to);

        assertThat(session.getScoreRestauration()).isEqualTo(16);
    }
}
