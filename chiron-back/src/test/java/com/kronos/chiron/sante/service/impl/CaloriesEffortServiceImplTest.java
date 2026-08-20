package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.sante.model.SanteFrequenceCardiaque;
import com.kronos.chiron.utilisateur.model.Sexe;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CaloriesEffortServiceImplTest {

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-20T10:00:00Z"), ZoneId.of("Europe/Paris"));

    @InjectMocks
    private CaloriesEffortServiceImpl service;

    private SanteFrequenceCardiaque bucket(double fc) {
        return SanteFrequenceCardiaque.builder().fcMoyenne(fc).build();
    }

    @Test
    void estimer_homme_returnsPositiveCalories() {
        Utilisateur user = Utilisateur.builder().id(1L).sexe(Sexe.HOMME).poidsCorps(80.0)
                .dateNaissance(LocalDate.of(1996, 8, 20)).build();
        List<SanteFrequenceCardiaque> buckets = List.of(bucket(140.0), bucket(150.0));

        int calories = service.estimer(user, buckets);

        assertThat(calories).isPositive();
    }

    @Test
    void estimer_femme_returnsPositiveCalories() {
        Utilisateur user = Utilisateur.builder().id(1L).sexe(Sexe.FEMME).poidsCorps(65.0)
                .dateNaissance(LocalDate.of(1996, 8, 20)).build();
        List<SanteFrequenceCardiaque> buckets = List.of(bucket(140.0), bucket(150.0));

        int calories = service.estimer(user, buckets);

        assertThat(calories).isPositive();
    }

    @Test
    void estimer_sexeAutreOuAbsent_moyenneHommeFemme() {
        Utilisateur homme = Utilisateur.builder().id(1L).sexe(Sexe.HOMME).poidsCorps(75.0)
                .dateNaissance(LocalDate.of(1996, 8, 20)).build();
        Utilisateur femme = Utilisateur.builder().id(2L).sexe(Sexe.FEMME).poidsCorps(75.0)
                .dateNaissance(LocalDate.of(1996, 8, 20)).build();
        Utilisateur autre = Utilisateur.builder().id(3L).sexe(Sexe.AUTRE).poidsCorps(75.0)
                .dateNaissance(LocalDate.of(1996, 8, 20)).build();
        List<SanteFrequenceCardiaque> buckets = List.of(bucket(140.0));

        int hommeCal = service.estimer(homme, buckets);
        int femmeCal = service.estimer(femme, buckets);
        int autreCal = service.estimer(autre, buckets);

        assertThat(autreCal).isBetween(Math.min(hommeCal, femmeCal), Math.max(hommeCal, femmeCal));
    }

    @Test
    void estimer_sexeNull_neLanceRienEtRenvoieUneMoyenne() {
        Utilisateur user = Utilisateur.builder().id(1L).poidsCorps(75.0)
                .dateNaissance(LocalDate.of(1996, 8, 20)).build();
        List<SanteFrequenceCardiaque> buckets = List.of(bucket(140.0));

        assertThat(service.estimer(user, buckets)).isPositive();
    }

    @Test
    void estimer_poidsEtDateNaissanceAbsents_utiliseLesDefauts() {
        Utilisateur user = Utilisateur.builder().id(1L).sexe(Sexe.HOMME).build();
        List<SanteFrequenceCardiaque> buckets = List.of(bucket(140.0));

        assertThat(service.estimer(user, buckets)).isPositive();
    }

    @Test
    void estimer_fcDeReposFaible_neRenvoiePasDeValeurNegative() {
        Utilisateur user = Utilisateur.builder().id(1L).sexe(Sexe.HOMME).poidsCorps(75.0)
                .dateNaissance(LocalDate.of(1996, 8, 20)).build();
        List<SanteFrequenceCardiaque> buckets = List.of(bucket(45.0));

        assertThat(service.estimer(user, buckets)).isGreaterThanOrEqualTo(0);
    }

    @Test
    void estimer_bucketsSansFcMoyenne_sontIgnores() {
        Utilisateur user = Utilisateur.builder().id(1L).sexe(Sexe.HOMME).poidsCorps(75.0)
                .dateNaissance(LocalDate.of(1996, 8, 20)).build();
        List<SanteFrequenceCardiaque> buckets = List.of(
                SanteFrequenceCardiaque.builder().fcMoyenne(null).build(), bucket(140.0));

        assertThat(service.estimer(user, buckets)).isPositive();
    }

    @Test
    void estimer_listeVide_renvoieZero() {
        Utilisateur user = Utilisateur.builder().id(1L).sexe(Sexe.HOMME).poidsCorps(75.0)
                .dateNaissance(LocalDate.of(1996, 8, 20)).build();

        assertThat(service.estimer(user, List.of())).isZero();
    }
}
