package com.kronos.chiron.sante.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.kronos.chiron.sante.model.SanteActivite;
import com.kronos.chiron.sante.model.SourceActivite;
import com.kronos.chiron.sante.model.TypeActivite;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.utilisateur.model.Sexe;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

class CaloriesSeanceServiceImplTest {

    private static final LocalDateTime DEBUT = LocalDateTime.of(2026, 8, 19, 18, 0);
    private static final LocalDateTime FIN = LocalDateTime.of(2026, 8, 19, 19, 15);

    private final CaloriesSeanceServiceImpl service = new CaloriesSeanceServiceImpl(
            Clock.fixed(Instant.parse("2026-08-19T20:00:00Z"), ZoneId.of("Europe/Paris")));

    private Utilisateur athlete(Double poids, Double taille) {
        return Utilisateur.builder().id(1L).username("athlete")
                .poidsCorps(poids).tailleCm(taille).sexe(Sexe.HOMME)
                .dateNaissance(LocalDate.of(1996, 8, 19))
                .build();
    }

    private SanteActivite activite(Utilisateur user, Seance seance) {
        return SanteActivite.builder()
                .id(10L).utilisateur(user).seance(seance)
                .source(SourceActivite.CHIRON_MUSCU).typeActivite(TypeActivite.MUSCULATION)
                .startTime(DEBUT).endTime(FIN)
                .build();
    }

    private Seance seanceAvec(Serie... series) {
        Seance seance = new Seance();
        seance.setStartTime(DEBUT);
        seance.setEndTime(FIN);
        Exercice exercice = new Exercice();
        exercice.setSeries(List.of(series));
        seance.setExercices(List.of(exercice));
        return seance;
    }

    private Serie serie(Double dureeMin, Double calories) {
        Serie serie = new Serie();
        serie.setDureeMin(dureeMin);
        serie.setCalories(calories);
        return serie;
    }

    // WHY: Mifflin-St Jeor pour 80 kg, 180 cm, 30 ans, homme rend 1780 kcal/jour, soit
    // 1,2361 kcal par minute au repos. Multiplié par le MET de la musculation et par les
    // 75 minutes de séance : 324 kcal. Le nombre est écrit ici parce qu'un modèle qui dérive
    // sans qu'on s'en aperçoive vaut moins qu'une case vide.
    @Test
    void estimer_seanceDeMusculationSeule_appliqueLeMetSurLeMetabolismeDeRepos() {
        // Given
        SanteActivite activite = activite(athlete(80.0, 180.0), seanceAvec(serie(null, null)));

        // When
        Integer kcal = service.estimer(activite);

        // Then
        assertThat(kcal).isEqualTo(324);
    }

    // WHY: le cardio a déjà ses calories, calculées série par série à partir de la distance et
    // de l'allure. Les recalculer autrement ferait mentir le total sur la somme que le journal
    // affiche juste au-dessus ; son temps sort donc du temps sous barre.
    @Test
    void estimer_serieCardio_ajouteSesCaloriesEtRetireSonTemps() {
        // Given
        SanteActivite activite = activite(athlete(80.0, 180.0), seanceAvec(serie(30.0, 350.0)));

        // When
        Integer kcal = service.estimer(activite);

        // Then
        assertThat(kcal).isEqualTo(545);
    }

    // WHY: 82 kg et non 80 — a 80 kg le calcul tombe pile sur 367,5, et le demi-point se joue
    // sur le dernier bit du flottant plutot que sur le modele. Le test dirait alors quelque
    // chose de la virgule flottante, rien de l'estimation.
    @Test
    void estimer_tailleInconnue_retombeSurLaConventionParKilo() {
        // Given
        SanteActivite activite = activite(athlete(82.0, null), seanceAvec(serie(null, null)));

        // When
        Integer kcal = service.estimer(activite);

        // Then
        assertThat(kcal).isEqualTo(377);
    }

    // WHY: le premier rattrapage en production a rendu 29 153 kcal sur une activité — environ
    // quatre jours et demi de musculation continue. Une séance qu'on a oublié de fermer garde une
    // durée qui ne dit plus rien du temps passé sous la barre : le cardio, lui, reste mesuré, et
    // c'est tout ce qui subsiste.
    @Test
    void estimer_seanceRestee0uverteDesJours_neChiffrePasLeTempsSousLaBarre() {
        // Given
        SanteActivite activite = activite(athlete(80.0, 180.0), seanceAvec(serie(30.0, 350.0)));
        activite.setEndTime(DEBUT.plusDays(5));

        // When
        Integer kcal = service.estimer(activite);

        // Then
        assertThat(kcal).isEqualTo(350);
    }

    @Test
    void estimer_seanceOuverteEtSansCardio_neRendRien() {
        // Given
        SanteActivite activite = activite(athlete(80.0, 180.0), seanceAvec(serie(null, null)));
        activite.setEndTime(DEBUT.plusDays(5));

        // When / Then
        assertThat(service.estimer(activite)).isNull();
    }

    // WHY: la borne en calories attrape ce que la borne de durée ne voit pas — une série dont les
    // calories enregistrées sont fausses. Aucune séance ne dépasse cinq mille kilocalories ; au
    // delà, on ne publie pas un chiffre qu'on ne sait pas justifier.
    @Test
    void estimer_caloriesDeSerieAberrantes_neRendRien() {
        // Given
        SanteActivite activite = activite(athlete(80.0, 180.0), seanceAvec(serie(30.0, 40000.0)));

        // When / Then
        assertThat(service.estimer(activite)).isNull();
    }

    @Test
    void estimer_sansSeance_neDevinePas() {
        // Given
        SanteActivite activite = activite(athlete(80.0, 180.0), null);

        // When / Then
        assertThat(service.estimer(activite)).isNull();
    }
}
