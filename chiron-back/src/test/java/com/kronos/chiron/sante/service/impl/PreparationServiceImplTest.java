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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PreparationServiceImplTest {

    @Mock
    private SanteJourRepository santeJourRepository;
    @Mock
    private SanteSommeilRepository santeSommeilRepository;

    @InjectMocks
    private PreparationServiceImpl preparationService;

    private Utilisateur user;

    // Relevé réel du 13 au 19 août : VFC, FC de repos, charge cardio, score de sommeil.
    private static final double[][] SEMAINE = {
            {13, 80.9, 52, 135, 84}, {14, 62, 53, 10, 80}, {15, 80.699, 53, 248, 77},
            {16, 67.4, 55, 0, 73}, {17, 103.4, 53, 2, 78}, {18, 112.9, 51, 129, 86},
            {19, 82.3, 52, 120, 87}};

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().id(1L).username("athlete").build();
        List<SanteJour> jours = new ArrayList<>();
        for (double[] l : SEMAINE) {
            LocalDate d = LocalDate.of(2026, 8, (int) l[0]);
            SanteJour j = SanteJour.builder().utilisateur(user).date(d).vfcMs(l[1])
                    .fcRepos((int) l[2]).chargeCardio(l[3]).build();
            jours.add(j);
            when(santeJourRepository.findByUtilisateurAndDate(user, d)).thenReturn(Optional.of(j));
            SanteSommeil nuit = SanteSommeil.builder().utilisateur(user).date(d).score((int) l[4]).build();
            when(santeSommeilRepository
                    .findFirstByUtilisateurAndDateAndSiesteFalseOrderByMinutesEndormiDesc(user, d))
                    .thenReturn(Optional.of(nuit));
        }
        when(santeJourRepository.findByUtilisateurAndDateBetweenOrderByDateAsc(eq(user), any(), any()))
                .thenReturn(jours);
    }

    @Test
    void calculer_restedDayAfterAnEasyEve_scoresHigh() {
        // Given : le 18/08 suit un 17/08 quasiment sans charge, avec une VFC au plus haut.
        // Google donne 99 ce jour-là.

        // When
        Integer score = preparationService.calculer(user, LocalDate.of(2026, 8, 18));

        // Then
        assertThat(score).isGreaterThanOrEqualTo(90);
    }

    @Test
    void calculer_dayAfterAHardSession_scoresLower() {
        // Given : le 19/08 suit une charge de 129 le 18. Google donne 69.

        // When
        Integer score = preparationService.calculer(user, LocalDate.of(2026, 8, 19));

        // Then
        assertThat(score).isBetween(55, 80);
    }

    @Test
    void calculer_neverFallsBelowGoogleLowZone() {
        // Given : le 16/08 cumule une VFC basse et une veille très dure — le pire cas du
        // relevé. Google reste pourtant à 67, son échelle ne s'effondrant pas.

        // When
        Integer score = preparationService.calculer(user, LocalDate.of(2026, 8, 16));

        // Then
        assertThat(score).isGreaterThanOrEqualTo(30);
    }

    @Test
    void calculer_noHeartRateVariability_returnsNothing() {
        // Given
        LocalDate date = LocalDate.of(2026, 8, 12);
        when(santeJourRepository.findByUtilisateurAndDate(user, date))
                .thenReturn(Optional.of(SanteJour.builder().utilisateur(user).date(date).build()));

        // When
        Integer score = preparationService.calculer(user, date);

        // Then
        assertThat(score).isNull();
    }
}
