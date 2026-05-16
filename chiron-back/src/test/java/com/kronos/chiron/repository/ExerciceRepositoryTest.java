package com.kronos.chiron.repository;

import com.kronos.chiron.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ExerciceRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ExerciceRepository exerciceRepository;

    private Utilisateur user;
    private Seance historicSession;
    private Seance templateSession;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder().username("athlete").password("p").role(Role.USER).isPublic(false).build();
        em.persist(user);

        historicSession = new Seance();
        historicSession.setTitre("Historic");
        historicSession.setModele(true);
        historicSession.setWeekNumber(1);
        historicSession.setStartTime(LocalDateTime.now().minusDays(7));
        historicSession.setUtilisateur(user);
        em.persist(historicSession);

        templateSession = new Seance();
        templateSession.setTitre("Template");
        templateSession.setModele(false);
        templateSession.setWeekNumber(0);
        templateSession.setStartTime(LocalDateTime.now().minusDays(1));
        templateSession.setUtilisateur(user);
        em.persist(templateSession);

        em.flush();
    }

    private Exercice makeExercice(String nom, Seance seance, LocalDateTime startTime) {
        Exercice e = new Exercice();
        e.setNom(nom);
        e.setSeance(seance);
        e.setStartTime(startTime);
        em.persist(e);
        em.flush();
        return e;
    }

    @Test
    void findFirstHistoricExercise_returnsLatestHistoricExercise() {
        makeExercice("Squat", historicSession, LocalDateTime.now().minusDays(10));
        makeExercice("Squat", historicSession, LocalDateTime.now().minusDays(3));
        makeExercice("Squat", historicSession, LocalDateTime.now().minusDays(1));

        Optional<Exercice> result = exerciceRepository.findFirstHistoricExercise(user.getId(), "Squat");

        assertThat(result).isPresent();
        assertThat(result.get().getStartTime()).isAfter(LocalDateTime.now().minusDays(2));
    }

    @Test
    void findFirstHistoricExercise_caseInsensitive() {
        makeExercice("SQUAT", historicSession, LocalDateTime.now().minusDays(1));

        Optional<Exercice> result = exerciceRepository.findFirstHistoricExercise(user.getId(), "squat");

        assertThat(result).isPresent();
    }

    @Test
    void findFirstHistoricExercise_excludesTemplates() {
        makeExercice("Bench", templateSession, LocalDateTime.now());
        makeExercice("Bench", historicSession, LocalDateTime.now().minusDays(5));

        // Only historic exercise found
        Optional<Exercice> result = exerciceRepository.findFirstHistoricExercise(user.getId(), "Bench");

        assertThat(result).isPresent();
        assertThat(result.get().getSeance().isModele()).isTrue();
    }

    @Test
    void findFirstHistoricExercise_notFound_returnsEmpty() {
        Optional<Exercice> result = exerciceRepository.findFirstHistoricExercise(user.getId(), "Deadlift");
        assertThat(result).isEmpty();
    }

    @Test
    void findAllHistoricExercises_returnsAllMatches() {
        makeExercice("Pull-up", historicSession, LocalDateTime.now().minusDays(7));
        makeExercice("Pull-up", historicSession, LocalDateTime.now().minusDays(3));
        makeExercice("Bench", historicSession, LocalDateTime.now().minusDays(2));

        List<Exercice> results = exerciceRepository.findAllHistoricExercises(user.getId(), "Pull-up");

        assertThat(results).hasSize(2);
    }

    @Test
    void findAllBySeanceUtilisateurIdAndNomContainingIgnoreCase_partialNameMatch() {
        makeExercice("Développé couché", historicSession, LocalDateTime.now().minusDays(1));
        makeExercice("Développé incliné", historicSession, LocalDateTime.now().minusDays(2));
        makeExercice("Squat", historicSession, LocalDateTime.now().minusDays(3));

        List<Exercice> results = exerciceRepository
                .findAllBySeanceUtilisateurIdAndNomContainingIgnoreCaseOrderByStartTimeDesc(
                        user.getId(), "développé");

        assertThat(results).hasSize(2);
    }
}
