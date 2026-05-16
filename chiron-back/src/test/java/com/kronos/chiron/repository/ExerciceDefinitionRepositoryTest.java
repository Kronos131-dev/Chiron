package com.kronos.chiron.repository;

import com.kronos.chiron.entity.ExerciceDefinition;
import com.kronos.chiron.entity.MuscleGroup;
import com.kronos.chiron.entity.NiveauDifficulte;
import com.kronos.chiron.entity.TypeEquipement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ExerciceDefinitionRepositoryTest {

    @Autowired private TestEntityManager em;
    @Autowired private ExerciceDefinitionRepository repository;

    private ExerciceDefinition bench;
    private ExerciceDefinition squat;
    private ExerciceDefinition pullUp;

    @BeforeEach
    void setUp() {
        bench = em.persist(ExerciceDefinition.builder()
                .externalId("Barbell_Bench_Press")
                .nomEn("Barbell Bench Press")
                .nomFr("Développé couché barre")
                .musclePrincipal(MuscleGroup.PECTORAUX)
                .typeEquipement(TypeEquipement.BARRE)
                .difficulte(NiveauDifficulte.INTERMEDIAIRE)
                .build());

        squat = em.persist(ExerciceDefinition.builder()
                .externalId("Barbell_Squat")
                .nomEn("Barbell Squat")
                .nomFr("Squat barre")
                .musclePrincipal(MuscleGroup.QUADRICEPS)
                .typeEquipement(TypeEquipement.BARRE)
                .difficulte(NiveauDifficulte.INTERMEDIAIRE)
                .build());

        pullUp = em.persist(ExerciceDefinition.builder()
                .externalId("Pull_Up")
                .nomEn("Pull-Up")
                .nomFr(null)
                .musclePrincipal(MuscleGroup.DOS)
                .typeEquipement(TypeEquipement.BARRE_FIXE)
                .difficulte(NiveauDifficulte.AVANCE)
                .build());

        em.flush();
    }

    @Test
    void findByExternalId_found() {
        Optional<ExerciceDefinition> result = repository.findByExternalId("Barbell_Squat");
        assertThat(result).isPresent();
        assertThat(result.get().getNomEn()).isEqualTo("Barbell Squat");
    }

    @Test
    void findByExternalId_notFound() {
        assertThat(repository.findByExternalId("unknown_id")).isEmpty();
    }

    @Test
    void search_noFilters_returnsAll() {
        List<ExerciceDefinition> results = repository.search(null, "%", null, null, null);
        assertThat(results).hasSize(3);
    }

    @Test
    void search_byQuery_matchesFrenchName() {
        List<ExerciceDefinition> results = repository.search("%développé%", "développé%", null, null, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExternalId()).isEqualTo("Barbell_Bench_Press");
    }

    @Test
    void search_byQuery_matchesEnglishName() {
        List<ExerciceDefinition> results = repository.search("%pull%", "pull%", null, null, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExternalId()).isEqualTo("Pull_Up");
    }

    @Test
    void search_byQuery_caseInsensitive() {
        // Le service pré-calcule en lowercase, le repo reçoit déjà en minuscules
        List<ExerciceDefinition> results = repository.search("%squat%", "squat%", null, null, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExternalId()).isEqualTo("Barbell_Squat");
    }

    @Test
    void search_byMuscle_returnsMuscleMatch() {
        List<ExerciceDefinition> results = repository.search(null, "%", MuscleGroup.PECTORAUX, null, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExternalId()).isEqualTo("Barbell_Bench_Press");
    }

    @Test
    void search_byEquipement_filtersCorrectly() {
        List<ExerciceDefinition> results = repository.search(null, "%", null, TypeEquipement.BARRE, null);
        assertThat(results).hasSize(2);
        assertThat(results).extracting(ExerciceDefinition::getExternalId)
                .containsExactlyInAnyOrder("Barbell_Bench_Press", "Barbell_Squat");
    }

    @Test
    void search_byDifficulte_filtersCorrectly() {
        List<ExerciceDefinition> results = repository.search(null, "%", null, null, NiveauDifficulte.AVANCE);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExternalId()).isEqualTo("Pull_Up");
    }

    @Test
    void search_combinedFilters_appliedTogether() {
        List<ExerciceDefinition> results = repository.search("%barbell%", "barbell%", MuscleGroup.QUADRICEPS, TypeEquipement.BARRE, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getExternalId()).isEqualTo("Barbell_Squat");
    }

    @Test
    void search_queryNoMatch_returnsEmpty() {
        List<ExerciceDefinition> results = repository.search("%xyzzyx%", "xyzzyx%", null, null, null);
        assertThat(results).isEmpty();
    }

    @Test
    void search_matchesExerciseWithNullFrName_byEnglishName() {
        List<ExerciceDefinition> results = repository.search("%pull-up%", "pull-up%", null, null, null);
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getNomFr()).isNull();
    }
}
