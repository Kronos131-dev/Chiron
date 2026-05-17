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
class SeanceRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private SeanceRepository seanceRepository;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder()
                .username("testuser")
                .password("pass")
                .role(Role.USER)
                .isPublic(false)
                .build();
        em.persist(user);
        em.flush();
    }

    private Seance makeSeance(String titre, boolean isModele, int weekNumber, LocalDateTime start, LocalDateTime end) {
        return makeSeance(titre, isModele, weekNumber, start, end, 0);
    }

    private Seance makeSeance(String titre, boolean isModele, int weekNumber, LocalDateTime start, LocalDateTime end, int displayOrder) {
        Seance s = new Seance();
        s.setTitre(titre);
        s.setModele(isModele);
        s.setWeekNumber(weekNumber);
        s.setStartTime(start);
        s.setEndTime(end);
        s.setDisplayOrder(displayOrder);
        s.setUtilisateur(user);
        em.persist(s);
        em.flush();
        return s;
    }

    @Test
    void findByUtilisateurIdAndWeekNumber_returnsCorrectSessions() {
        makeSeance("Week5Session", true, 5, LocalDateTime.now(), LocalDateTime.now());
        makeSeance("Week6Session", true, 6, LocalDateTime.now(), LocalDateTime.now());

        List<Seance> results = seanceRepository
                .findByUtilisateurIdAndWeekNumberOrderByStartTimeDesc(user.getId(), 5);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitre()).isEqualTo("Week5Session");
    }

    @Test
    void findFirstByUtilisateurIdAndEndTimeIsNull_returnsActiveSession() {
        makeSeance("Finished", true, 1, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1));
        makeSeance("Active", true, 1, LocalDateTime.now().minusMinutes(30), null);

        Optional<Seance> result = seanceRepository
                .findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(user.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getTitre()).isEqualTo("Active");
    }

    @Test
    void findFirstByUtilisateurIdAndEndTimeIsNull_noActiveSession_returnsEmpty() {
        makeSeance("Closed", true, 1, LocalDateTime.now().minusHours(1), LocalDateTime.now());

        Optional<Seance> result = seanceRepository
                .findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(user.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findByIsModeleTrue_returnsOnlyRealSessions() {
        makeSeance("RealSession", true, 1, LocalDateTime.now(), LocalDateTime.now());
        makeSeance("Template", false, 0, LocalDateTime.now(), null);

        List<Seance> results = seanceRepository
                .findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(user.getUsername());

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getTitre()).isEqualTo("RealSession");
    }

    @Test
    void findByIsModeleFalse_returnsOnlyTemplates() {
        makeSeance("RealSession", true, 1, LocalDateTime.now(), LocalDateTime.now());
        makeSeance("Template1", false, 0, LocalDateTime.now(), null);
        makeSeance("Template2", false, 0, LocalDateTime.now().minusDays(1), null);

        List<Seance> results = seanceRepository
                .findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc(user.getUsername());

        assertThat(results).hasSize(2);
    }

    @Test
    void findByIsModeleFalse_orderedByDisplayOrderAsc() {
        makeSeance("Third",  false, 0, LocalDateTime.now(),               null, 2);
        makeSeance("First",  false, 0, LocalDateTime.now().minusDays(5),  null, 0);
        makeSeance("Second", false, 0, LocalDateTime.now().minusDays(1),  null, 1);

        List<Seance> results = seanceRepository
                .findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc(user.getUsername());

        assertThat(results).extracting(Seance::getTitre)
                .containsExactly("First", "Second", "Third");
    }

    @Test
    void findByIsModeleFalse_displayOrderTie_fallsBackToStartTimeDesc() {
        // Same displayOrder → newer startTime wins (so brand-new programmes appear at top).
        makeSeance("Older Default", false, 0, LocalDateTime.now().minusDays(3), null, 0);
        makeSeance("Newer Default", false, 0, LocalDateTime.now(),              null, 0);

        List<Seance> results = seanceRepository
                .findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc(user.getUsername());

        assertThat(results.get(0).getTitre()).isEqualTo("Newer Default");
        assertThat(results.get(1).getTitre()).isEqualTo("Older Default");
    }

    @Test
    void countTotalSeriesForUserSince_countsCorrectly() {
        Seance session = makeSeance("Push", true, 1, LocalDateTime.now().minusDays(1), LocalDateTime.now());
        Exercice exo = new Exercice();
        exo.setNom("Bench");
        exo.setSeance(session);
        em.persist(exo);

        Serie s1 = new Serie(); s1.setPoids(80); s1.setNombreReps(5); s1.setExercice(exo);
        Serie s2 = new Serie(); s2.setPoids(85); s2.setNombreReps(4); s2.setExercice(exo);
        em.persist(s1);
        em.persist(s2);
        em.flush();

        Integer count = seanceRepository.countTotalSeriesForUserSince(
                user.getId(), LocalDateTime.now().minusDays(7));

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countTotalSeriesForUserSince_excludesTemplates() {
        Seance template = makeSeance("Template", false, 0, LocalDateTime.now().minusDays(1), null);
        Exercice exo = new Exercice();
        exo.setNom("Squat");
        exo.setSeance(template);
        em.persist(exo);
        Serie s = new Serie(); s.setPoids(100); s.setNombreReps(5); s.setExercice(exo);
        em.persist(s);
        em.flush();

        Integer count = seanceRepository.countTotalSeriesForUserSince(
                user.getId(), LocalDateTime.now().minusDays(7));

        assertThat(count).isEqualTo(0);
    }

    @Test
    void exercicesOrder_persistsAndReloadsInListPosition() {
        Seance template = makeSeance("Push Day", false, 0, LocalDateTime.now(), null);

        Exercice bench = new Exercice(); bench.setNom("Bench");
        Exercice dips  = new Exercice(); dips.setNom("Dips");
        Exercice ohp   = new Exercice(); ohp.setNom("OHP");

        // Initial save: insert in [Bench, Dips, OHP] order.
        template.addExercice(bench);
        template.addExercice(dips);
        template.addExercice(ohp);
        em.flush();
        em.clear();

        Seance reloaded = em.find(Seance.class, template.getId());
        assertThat(reloaded.getExercices())
                .extracting(Exercice::getNom)
                .containsExactly("Bench", "Dips", "OHP");

        // Reorder to [OHP, Bench, Dips] by mutating the list and re-saving.
        Exercice moved = reloaded.getExercices().remove(2);
        reloaded.getExercices().add(0, moved);
        em.flush();
        em.clear();

        Seance afterReorder = em.find(Seance.class, template.getId());
        assertThat(afterReorder.getExercices())
                .extracting(Exercice::getNom)
                .containsExactly("OHP", "Bench", "Dips");
    }

    @Test
    void countTotalSeriesForUserSince_excludesBeyondDateRange() {
        Seance oldSession = makeSeance("OldPush", true, 1,
                LocalDateTime.now().minusDays(40), LocalDateTime.now().minusDays(40));
        Exercice exo = new Exercice();
        exo.setNom("OldBench");
        exo.setSeance(oldSession);
        em.persist(exo);
        Serie s = new Serie(); s.setPoids(80); s.setNombreReps(5); s.setExercice(exo);
        em.persist(s);
        em.flush();

        Integer count = seanceRepository.countTotalSeriesForUserSince(
                user.getId(), LocalDateTime.now().minusDays(30));

        assertThat(count).isEqualTo(0);
    }
}
