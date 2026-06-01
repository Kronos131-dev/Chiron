package com.kronos.chiron.stats;

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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Vérifie que les jours sans apport (kcal nulle ou 0) sont exclus des stats nutrition.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatsServiceNutritionTest {

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
        when(nutritionService.getOlympusUserId("athlete")).thenReturn(Optional.of(7L));
    }

    @Test
    void getNutrition_excludesZeroAndNullKcalDays() {
        List<NutritionPointDto> jours = List.of(
                new NutritionPointDto(LocalDate.of(2026, 5, 1), 2000.0, 150.0, 200.0, 60.0, 2200.0, 8000),
                new NutritionPointDto(LocalDate.of(2026, 5, 2), 0.0, 0.0, 0.0, 0.0, 2200.0, 3000),   // non renseigné
                new NutritionPointDto(LocalDate.of(2026, 5, 3), null, null, null, null, 2200.0, 0),  // aucune donnée
                new NutritionPointDto(LocalDate.of(2026, 5, 4), 1000.0, 100.0, 100.0, 30.0, 2200.0, 5000));
        when(olympusDao.dailyNutrition(eq(7L), any(), any())).thenReturn(jours);

        NutritionStatsDto stats = statsService.getNutrition("athlete", 30);

        assertThat(stats.linked()).isTrue();
        assertThat(stats.available()).isTrue();
        // Seuls les 2 jours avec apport réel sont conservés.
        assertThat(stats.jours()).hasSize(2);
        assertThat(stats.moyKcal()).isEqualTo(1500.0);   // (2000 + 1000) / 2, pas /4
        assertThat(stats.moyProteines()).isEqualTo(125.0); // (150 + 100) / 2
    }

    @Test
    void getNutrition_allZeroDays_returnsEmptyAverages() {
        List<NutritionPointDto> jours = List.of(
                new NutritionPointDto(LocalDate.of(2026, 5, 1), 0.0, 0.0, 0.0, 0.0, 2200.0, 1000));
        when(olympusDao.dailyNutrition(eq(7L), any(), any())).thenReturn(jours);

        NutritionStatsDto stats = statsService.getNutrition("athlete", 30);

        assertThat(stats.jours()).isEmpty();
        assertThat(stats.moyKcal()).isNull();
    }
}
