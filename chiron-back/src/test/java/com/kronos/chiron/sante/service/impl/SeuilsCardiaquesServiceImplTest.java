package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.dto.SeuilsCardiaquesDto;
import com.kronos.chiron.sante.model.SanteJour;
import com.kronos.chiron.sante.persistence.SanteJourRepository;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SeuilsCardiaquesServiceImplTest {

    @Mock
    private SanteJourRepository santeJourRepository;

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-20T10:00:00Z"), ZoneId.of("Europe/Paris"));

    @InjectMocks
    private SeuilsCardiaquesServiceImpl service;

    @Test
    void calculer_ageEtFcReposConnus_appliqueKarvonen() {
        Utilisateur user = Utilisateur.builder().id(1L).dateNaissance(LocalDate.of(1996, 8, 20)).build();
        SanteJour jour = SanteJour.builder().fcRepos(60).build();
        when(santeJourRepository.findFirstByUtilisateurAndFcReposIsNotNullAndDateBetweenOrderByDateDesc(any(), any(),
                any())).thenReturn(Optional.of(jour));

        SeuilsCardiaquesDto seuils = service.calculer(user);

        // âge 30, FCmax 190, réserve 130 (190-60)
        assertThat(seuils.modere()).isEqualTo(60 + (int) Math.round(0.50 * 130));
        assertThat(seuils.intense()).isEqualTo(60 + (int) Math.round(0.70 * 130));
        assertThat(seuils.maximum()).isEqualTo(60 + (int) Math.round(0.85 * 130));
    }

    @Test
    void calculer_dateNaissanceAbsente_utiliseAgeParDefaut() {
        Utilisateur user = Utilisateur.builder().id(1L).build();
        SanteJour jour = SanteJour.builder().fcRepos(60).build();
        when(santeJourRepository.findFirstByUtilisateurAndFcReposIsNotNullAndDateBetweenOrderByDateDesc(any(), any(),
                any())).thenReturn(Optional.of(jour));

        SeuilsCardiaquesDto seuils = service.calculer(user);

        // âge par défaut 30 -> FCmax 190, réserve 130
        assertThat(seuils.maximum()).isEqualTo(60 + (int) Math.round(0.85 * 130));
    }

    @Test
    void calculer_fcReposAbsente_utiliseFcReposParDefaut() {
        Utilisateur user = Utilisateur.builder().id(1L).dateNaissance(LocalDate.of(1996, 8, 20)).build();
        when(santeJourRepository.findFirstByUtilisateurAndFcReposIsNotNullAndDateBetweenOrderByDateDesc(any(), any(),
                any())).thenReturn(Optional.empty());

        SeuilsCardiaquesDto seuils = service.calculer(user);

        // FC repos par défaut 60, réserve 190-60 = 130
        assertThat(seuils.modere()).isEqualTo(60 + (int) Math.round(0.50 * 130));
    }

    @Test
    void calculer_seuilsCroissants() {
        Utilisateur user = Utilisateur.builder().id(1L).dateNaissance(LocalDate.of(1986, 1, 1)).build();
        SanteJour jour = SanteJour.builder().fcRepos(55).build();
        when(santeJourRepository.findFirstByUtilisateurAndFcReposIsNotNullAndDateBetweenOrderByDateDesc(any(), any(),
                any())).thenReturn(Optional.of(jour));

        SeuilsCardiaquesDto seuils = service.calculer(user);

        assertThat(seuils.modere()).isLessThan(seuils.intense());
        assertThat(seuils.intense()).isLessThan(seuils.maximum());
    }
}
