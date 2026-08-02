package com.kronos.chiron.journalier.service.impl;

import org.mockito.Spy;

import java.time.ZoneId;

import java.time.Clock;

import com.kronos.chiron.journalier.model.EtatJournalier;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.journalier.persistence.EtatJournalierRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecoveryServiceTest {

    private static final LocalDate DATE = LocalDate.of(2026, 3, 15);

    @Mock
    private EtatJournalierRepository repository;

    @Spy
    private Clock clock = Clock.system(ZoneId.of("Europe/Paris"));

    @InjectMocks
    private RecoveryServiceImpl recoveryService;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = new Utilisateur();
        user.setId(1L);
    }

    private void givenNoExistingEtat() {
        when(repository.findByUtilisateurAndDate(user, DATE)).thenReturn(Optional.empty());
    }

    private void givenExistingEtat(EtatJournalier etat) {
        when(repository.findByUtilisateurAndDate(user, DATE)).thenReturn(Optional.of(etat));
    }

    private EtatJournalier captureSaved() {
        ArgumentCaptor<EtatJournalier> captor = ArgumentCaptor.forClass(EtatJournalier.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void upsert_noExistingEtat_createsOneForTheUserAndDate() {
        givenNoExistingEtat();
        when(repository.save(any(EtatJournalier.class))).thenAnswer(i -> i.getArgument(0));

        recoveryService.upsert(user, DATE, 7.5, 2, 3, 1, 4, "bien dormi");

        EtatJournalier saved = captureSaved();
        assertThat(saved.getUtilisateur()).isSameAs(user);
        assertThat(saved.getDate()).isEqualTo(DATE);
        assertThat(saved.getSommeilHeures()).isEqualTo(7.5);
        assertThat(saved.getNotes()).isEqualTo("bien dormi");
    }

    @Test
    void upsert_existingEtat_updatesItInPlace() {
        EtatJournalier existing = EtatJournalier.builder()
                .utilisateur(user).date(DATE).sommeilHeures(6.0).build();
        givenExistingEtat(existing);
        when(repository.save(any(EtatJournalier.class))).thenAnswer(i -> i.getArgument(0));

        recoveryService.upsert(user, DATE, 8.0, null, null, null, null, null);

        assertThat(captureSaved()).isSameAs(existing);
        assertThat(existing.getSommeilHeures()).isEqualTo(8.0);
    }

    @Test
    void upsert_nullFields_leaveExistingValuesUntouched() {
        EtatJournalier existing = EtatJournalier.builder()
                .utilisateur(user).date(DATE)
                .sommeilHeures(6.0).fatigue(3).courbatures(2).stress(4).energie(5).notes("note")
                .build();
        givenExistingEtat(existing);
        when(repository.save(any(EtatJournalier.class))).thenAnswer(i -> i.getArgument(0));

        recoveryService.upsert(user, DATE, null, null, null, null, null, null);

        assertThat(existing.getSommeilHeures()).isEqualTo(6.0);
        assertThat(existing.getFatigue()).isEqualTo(3);
        assertThat(existing.getCourbatures()).isEqualTo(2);
        assertThat(existing.getStress()).isEqualTo(4);
        assertThat(existing.getEnergie()).isEqualTo(5);
        assertThat(existing.getNotes()).isEqualTo("note");
    }

    @Test
    void upsert_scoreBelowOne_isClampedToOne() {
        givenNoExistingEtat();
        when(repository.save(any(EtatJournalier.class))).thenAnswer(i -> i.getArgument(0));

        recoveryService.upsert(user, DATE, null, -3, 0, -1, 0, null);

        EtatJournalier saved = captureSaved();
        assertThat(saved.getFatigue()).isEqualTo(1);
        assertThat(saved.getCourbatures()).isEqualTo(1);
        assertThat(saved.getStress()).isEqualTo(1);
        assertThat(saved.getEnergie()).isEqualTo(1);
    }

    @Test
    void upsert_scoreAboveFive_isClampedToFive() {
        givenNoExistingEtat();
        when(repository.save(any(EtatJournalier.class))).thenAnswer(i -> i.getArgument(0));

        recoveryService.upsert(user, DATE, null, 9, 100, 6, 42, null);

        EtatJournalier saved = captureSaved();
        assertThat(saved.getFatigue()).isEqualTo(5);
        assertThat(saved.getCourbatures()).isEqualTo(5);
        assertThat(saved.getStress()).isEqualTo(5);
        assertThat(saved.getEnergie()).isEqualTo(5);
    }

    @Test
    void upsert_scoreWithinRange_isKeptAsIs() {
        givenNoExistingEtat();
        when(repository.save(any(EtatJournalier.class))).thenAnswer(i -> i.getArgument(0));

        recoveryService.upsert(user, DATE, null, 1, 3, 5, 4, null);

        EtatJournalier saved = captureSaved();
        assertThat(saved.getFatigue()).isEqualTo(1);
        assertThat(saved.getCourbatures()).isEqualTo(3);
        assertThat(saved.getStress()).isEqualTo(5);
        assertThat(saved.getEnergie()).isEqualTo(4);
    }

    @Test
    void upsert_blankNotes_areIgnored() {
        givenNoExistingEtat();
        when(repository.save(any(EtatJournalier.class))).thenAnswer(i -> i.getArgument(0));

        recoveryService.upsert(user, DATE, null, null, null, null, null, "   ");

        assertThat(captureSaved().getNotes()).isNull();
    }

    @Test
    void upsert_notesAreTrimmed() {
        givenNoExistingEtat();
        when(repository.save(any(EtatJournalier.class))).thenAnswer(i -> i.getArgument(0));

        recoveryService.upsert(user, DATE, null, null, null, null, null, "  fatigué  ");

        assertThat(captureSaved().getNotes()).isEqualTo("fatigué");
    }

    @Test
    void upsertFromFitbit_nullSleep_writesNothing() {
        assertThat(recoveryService.upsertFromFitbit(user, DATE, null)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void upsertFromFitbit_zeroSleep_writesNothing() {
        assertThat(recoveryService.upsertFromFitbit(user, DATE, 0.0)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void upsertFromFitbit_negativeSleep_writesNothing() {
        assertThat(recoveryService.upsertFromFitbit(user, DATE, -2.0)).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void upsertFromFitbit_noExistingEtat_createsItAndWritesSleep() {
        givenNoExistingEtat();

        assertThat(recoveryService.upsertFromFitbit(user, DATE, 7.25)).isTrue();
        assertThat(captureSaved().getSommeilHeures()).isEqualTo(7.25);
    }

    @Test
    void upsertFromFitbit_manualSleepAlreadyPresent_neverOverwritesIt() {
        EtatJournalier existing = EtatJournalier.builder()
                .utilisateur(user).date(DATE).sommeilHeures(6.0).build();
        givenExistingEtat(existing);

        assertThat(recoveryService.upsertFromFitbit(user, DATE, 8.0)).isFalse();
        assertThat(existing.getSommeilHeures()).isEqualTo(6.0);
        verify(repository, never()).save(any());
    }

    @Test
    void upsertFromFitbit_existingEtatWithoutSleep_fillsIt() {
        EtatJournalier existing = EtatJournalier.builder()
                .utilisateur(user).date(DATE).fatigue(3).build();
        givenExistingEtat(existing);

        assertThat(recoveryService.upsertFromFitbit(user, DATE, 8.0)).isTrue();
        assertThat(existing.getSommeilHeures()).isEqualTo(8.0);
    }

    @Test
    void getRecent_queriesFromTheRequestedNumberOfDays() {
        when(repository.findByUtilisateurAndDateGreaterThanEqualOrderByDateDesc(
                user, LocalDate.now().minusDays(6))).thenReturn(List.of());

        assertThat(recoveryService.getRecent(user, 7)).isEmpty();
        verify(repository).findByUtilisateurAndDateGreaterThanEqualOrderByDateDesc(
                user, LocalDate.now().minusDays(6));
    }

    @Test
    void getRecent_zeroDays_isClampedToOneDay() {
        when(repository.findByUtilisateurAndDateGreaterThanEqualOrderByDateDesc(
                user, LocalDate.now())).thenReturn(List.of());

        recoveryService.getRecent(user, 0);

        verify(repository).findByUtilisateurAndDateGreaterThanEqualOrderByDateDesc(
                user, LocalDate.now());
    }

    @Test
    void getRecent_moreThanNinetyDays_isClampedToNinety() {
        when(repository.findByUtilisateurAndDateGreaterThanEqualOrderByDateDesc(
                user, LocalDate.now().minusDays(89))).thenReturn(List.of());

        recoveryService.getRecent(user, 365);

        verify(repository).findByUtilisateurAndDateGreaterThanEqualOrderByDateDesc(
                user, LocalDate.now().minusDays(89));
    }
}
