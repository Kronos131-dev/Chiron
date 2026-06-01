package com.kronos.chiron.stats;

import com.kronos.chiron.entity.Exercice;
import com.kronos.chiron.entity.ExerciceDefinition;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.entity.Serie;
import com.kronos.chiron.entity.TypeEquipement;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.nutrition.NutritionService;
import com.kronos.chiron.nutrition.olympusdb.OlympusNutritionDao;
import com.kronos.chiron.repository.SeanceRepository;
import com.kronos.chiron.repository.UtilisateurRepository;
import com.kronos.chiron.service.OneRepMaxEstimator;
import com.kronos.chiron.service.PerformanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Vérifie la règle de tonnage ×2 (haltères / unilatéral / machine par côté) via getWeeklyVolume.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatsServiceTonnageTest {

    @Mock private SeanceRepository seanceRepository;
    @Mock private PerformanceService performanceService;
    @Mock private NutritionService nutritionService;
    @Mock private OlympusNutritionDao olympusDao;
    @Mock private com.kronos.chiron.visbody.BodyCompositionRecordRepository bodyCompositionRecordRepository;
    @Mock private UtilisateurRepository utilisateurRepository;

    private StatsService statsService;

    @BeforeEach
    void setUp() {
        statsService = new StatsService(seanceRepository, performanceService, nutritionService,
                olympusDao, new OneRepMaxEstimator(), bodyCompositionRecordRepository, utilisateurRepository);
        Utilisateur user = Utilisateur.builder().username("athlete").poidsMachineParCote(false).build();
        when(utilisateurRepository.findByUsername("athlete")).thenReturn(Optional.of(user));
    }

    private Seance sessionWith(TypeEquipement equip, boolean unilateral, double poids, int reps) {
        ExerciceDefinition def = equip != null
                ? ExerciceDefinition.builder().typeEquipement(equip).build() : null;
        Serie serie = Serie.builder().poids(poids).nombreReps(reps).build();
        Exercice exo = Exercice.builder().definition(def).unilateral(unilateral).series(List.of(serie)).build();
        return Seance.builder().startTime(LocalDateTime.now()).isModele(true).exercices(List.of(exo)).build();
    }

    private double weeklyTonnage(Seance s) {
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc("athlete"))
                .thenReturn(List.of(s));
        return statsService.getWeeklyVolume("athlete", 1).stream()
                .mapToDouble(WeeklyVolumePointDto::tonnage).sum();
    }

    @Test
    void halteres_doublesTonnage() {
        assertThat(weeklyTonnage(sessionWith(TypeEquipement.HALTERES, false, 30, 10)))
                .isEqualTo(600.0); // 2 × 30 × 10
    }

    @Test
    void unilateral_doublesTonnage() {
        assertThat(weeklyTonnage(sessionWith(TypeEquipement.MACHINE, true, 40, 12)))
                .isEqualTo(960.0); // 2 × 40 × 12
    }

    @Test
    void barbell_keepsSingleFactor() {
        assertThat(weeklyTonnage(sessionWith(TypeEquipement.BARRE, false, 100, 5)))
                .isEqualTo(500.0); // 1 × 100 × 5
    }

    @Test
    void machineBilateral_keepsSingleFactor() {
        assertThat(weeklyTonnage(sessionWith(TypeEquipement.MACHINE, false, 200, 10)))
                .isEqualTo(2000.0); // 1 × 200 × 10
    }

    @Test
    void freeTextExercise_noDefinition_keepsSingleFactor() {
        assertThat(weeklyTonnage(sessionWith(null, false, 50, 8)))
                .isEqualTo(400.0); // 1 × 50 × 8
    }
}
