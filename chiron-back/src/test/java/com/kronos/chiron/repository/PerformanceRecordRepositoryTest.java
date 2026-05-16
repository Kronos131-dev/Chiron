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
class PerformanceRecordRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private PerformanceRecordRepository repository;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder()
                .username("perf_user").password("p").role(Role.USER).isPublic(false).build();
        em.persist(user);
        em.flush();
    }

    private PerformanceRecord save(ExerciseType type, double poids, int reps, double rm1, LocalDateTime at) {
        PerformanceRecord r = PerformanceRecord.builder()
                .utilisateur(user)
                .exerciseType(type)
                .poids(poids)
                .nombreReps(reps)
                .rm1Estime(rm1)
                .recordedAt(at)
                .build();
        em.persist(r);
        em.flush();
        return r;
    }

    @Test
    void findByUtilisateurIdOrderByRecordedAtDesc_returnsNewestFirst() {
        save(ExerciseType.SQUAT, 100, 5, 112.5, LocalDateTime.now().minusDays(10));
        save(ExerciseType.SQUAT, 110, 5, 123.75, LocalDateTime.now().minusDays(1));

        List<PerformanceRecord> results = repository.findByUtilisateurIdOrderByRecordedAtDesc(user.getId());

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getPoids()).isEqualTo(110.0);
    }

    @Test
    void findByUtilisateurIdAndExerciseType_filtersCorrectly() {
        save(ExerciseType.SQUAT, 100, 5, 112.5, LocalDateTime.now().minusDays(2));
        save(ExerciseType.DEVELOPPE_COUCHE, 80, 5, 90.0, LocalDateTime.now().minusDays(1));

        List<PerformanceRecord> squats = repository.findByUtilisateurIdAndExerciseTypeOrderByRecordedAtDesc(
                user.getId(), ExerciseType.SQUAT);

        assertThat(squats).hasSize(1);
        assertThat(squats.get(0).getExerciseType()).isEqualTo(ExerciseType.SQUAT);
    }

    @Test
    void findFirstByExerciseTypeOrderByRm1EstimeDesc_returnsBestRecord() {
        save(ExerciseType.SQUAT, 100, 5, 112.5, LocalDateTime.now().minusDays(3));
        save(ExerciseType.SQUAT, 120, 5, 135.0, LocalDateTime.now().minusDays(2));
        save(ExerciseType.SQUAT, 90, 5, 101.25, LocalDateTime.now().minusDays(1));

        Optional<PerformanceRecord> best = repository
                .findFirstByUtilisateurIdAndExerciseTypeOrderByRm1EstimeDesc(user.getId(), ExerciseType.SQUAT);

        assertThat(best).isPresent();
        assertThat(best.get().getRm1Estime()).isEqualTo(135.0);
    }

    @Test
    void findFirstByExerciseTypeOrderByRecordedAtDesc_returnsMostRecent() {
        save(ExerciseType.DEVELOPPE_COUCHE, 80, 5, 90.0, LocalDateTime.now().minusDays(5));
        save(ExerciseType.DEVELOPPE_COUCHE, 85, 5, 95.6, LocalDateTime.now().minusDays(1));

        Optional<PerformanceRecord> latest = repository
                .findFirstByUtilisateurIdAndExerciseTypeOrderByRecordedAtDesc(
                        user.getId(), ExerciseType.DEVELOPPE_COUCHE);

        assertThat(latest).isPresent();
        assertThat(latest.get().getPoids()).isEqualTo(85.0);
    }

    @Test
    void findByUtilisateurId_noRecords_returnsEmptyList() {
        List<PerformanceRecord> results = repository.findByUtilisateurIdOrderByRecordedAtDesc(user.getId());
        assertThat(results).isEmpty();
    }

    @Test
    void findFirstByExerciseType_noRecords_returnsEmpty() {
        Optional<PerformanceRecord> result = repository
                .findFirstByUtilisateurIdAndExerciseTypeOrderByRecordedAtDesc(
                        user.getId(), ExerciseType.TRACTIONS);
        assertThat(result).isEmpty();
    }
}
