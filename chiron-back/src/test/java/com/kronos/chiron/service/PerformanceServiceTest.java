package com.kronos.chiron.service;

import com.kronos.chiron.dto.ExercisePerformanceDto;
import com.kronos.chiron.dto.PerformanceRecordDto;
import com.kronos.chiron.dto.PerformanceSummaryDto;
import com.kronos.chiron.entity.*;
import com.kronos.chiron.repository.PerformanceRecordRepository;
import com.kronos.chiron.repository.UtilisateurRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PerformanceServiceTest {

    @Mock private PerformanceRecordRepository performanceRecordRepository;
    @Mock private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private PerformanceService performanceService;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder()
                .id(1L)
                .username("athlete")
                .poidsCorps(80.0)
                .build();
        when(utilisateurRepository.findByUsername("athlete")).thenReturn(Optional.of(user));
        when(performanceRecordRepository.findFirstByUtilisateurIdAndExerciseTypeOrderByRecordedAtDesc(any(), any()))
                .thenReturn(Optional.empty());
    }

    // --- calculateRm1 ---

    @Test
    void calculateRm1_barbellExercise_usesBarWeight() {
        double rm1 = performanceService.calculateRm1(ExerciseType.DEVELOPPE_COUCHE, 100.0, 5, 80.0);
        // 100 * (36 / (37 - 5)) = 100 * 36/32 = 112.5
        assertThat(rm1).isCloseTo(112.5, within(0.1));
    }

    @Test
    void calculateRm1_bodyweightExercise_addsPoidsCorporel() {
        // TRACTIONS: effectiveWeight = lest(0) + bodyweight(80) = 80; 10 reps capped
        double rm1 = performanceService.calculateRm1(ExerciseType.TRACTIONS, 0.0, 8, 80.0);
        // effectiveReps = 8 (< 10), effectiveWeight = 80; 80 * (36/(37-8)) = 80 * 36/29 ≈ 99.31
        assertThat(rm1).isCloseTo(99.31, within(0.1));
    }

    @Test
    void calculateRm1_bodyweightWithExtraWeight_addsLest() {
        // TRACTIONS: lest=20, poidsCorps=80 → effectiveWeight=100; 5 reps
        double rm1 = performanceService.calculateRm1(ExerciseType.TRACTIONS, 20.0, 5, 80.0);
        // 100 * (36/(37-5)) = 100 * 36/32 = 112.5
        assertThat(rm1).isCloseTo(112.5, within(0.1));
    }

    @Test
    void calculateRm1_bodyweightNoLest_capsAt10Reps() {
        // 15 reps declared, but lest=0 → capped at 10
        double rm1_15reps = performanceService.calculateRm1(ExerciseType.TRACTIONS, 0.0, 15, 80.0);
        double rm1_10reps = performanceService.calculateRm1(ExerciseType.TRACTIONS, 0.0, 10, 80.0);
        assertThat(rm1_15reps).isEqualTo(rm1_10reps);
    }

    @Test
    void calculateRm1_bodyweightNullBodyweight_usesOnlyLest() {
        double rm1 = performanceService.calculateRm1(ExerciseType.TRACTIONS, 30.0, 5, null);
        // no bodyweight → effectiveWeight = poids (30) only
        assertThat(rm1).isCloseTo(30.0 * 36.0 / 32.0, within(0.01));
    }

    // --- tierForRatio ---

    @Test
    void tierForRatio_nullRatio_returnsEphebe() {
        assertThat(performanceService.tierForRatio(ExerciseType.DEVELOPPE_COUCHE, null))
                .isEqualTo(PerformanceTier.EPHEBE);
    }

    @Test
    void tierForRatio_belowAllThresholds_returnsEphebe() {
        // Bench: first threshold is 0.70; ratio = 0.5
        assertThat(performanceService.tierForRatio(ExerciseType.DEVELOPPE_COUCHE, 0.5))
                .isEqualTo(PerformanceTier.EPHEBE);
    }

    @Test
    void tierForRatio_aboveFirstThreshold_returnsArgonaute() {
        // Bench: Argonaute >= 0.70; below 0.85 for Hoplite
        assertThat(performanceService.tierForRatio(ExerciseType.DEVELOPPE_COUCHE, 0.72))
                .isEqualTo(PerformanceTier.ARGONAUTE);
    }

    @Test
    void tierForRatio_aboveAllThresholds_returnsOlympien() {
        // Bench: Olympien >= 1.60
        assertThat(performanceService.tierForRatio(ExerciseType.DEVELOPPE_COUCHE, 2.0))
                .isEqualTo(PerformanceTier.OLYMPIEN);
    }

    @Test
    void tierForRatio_squat_exactThreshold_returnsTier() {
        // Squat: Spartiate >= 1.50
        assertThat(performanceService.tierForRatio(ExerciseType.SQUAT, 1.50))
                .isEqualTo(PerformanceTier.SPARTIATE);
    }

    // --- getSummary ---

    @Test
    void getSummary_noRecords_returnsEphebeForAllExercises() {
        PerformanceSummaryDto summary = performanceService.getSummary("athlete");

        assertThat(summary.getExercises()).hasSize(ExerciseType.values().length);
        assertThat(summary.getExercises())
                .allMatch(e -> e.getTier().equals(PerformanceTier.EPHEBE.getNom()));
        assertThat(summary.getOverallTier()).isEqualTo(PerformanceTier.EPHEBE.getNom());
    }

    @Test
    void getSummary_userNotFound_throwsException() {
        when(utilisateurRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> performanceService.getSummary("unknown"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("introuvable");
    }

    // --- addRecord ---

    @Test
    void addRecord_validData_savesRecord() {
        PerformanceRecordDto dto = new PerformanceRecordDto("DEVELOPPE_COUCHE", 100.0, 5);
        when(performanceRecordRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        performanceService.addRecord("athlete", dto);

        verify(performanceRecordRepository).saveAndFlush(argThat(r ->
                r.getExerciseType() == ExerciseType.DEVELOPPE_COUCHE &&
                r.getPoids() == 100.0 &&
                r.getNombreReps() == 5
        ));
    }

    @Test
    void addRecord_invalidReps_zero_throwsException() {
        PerformanceRecordDto dto = new PerformanceRecordDto("SQUAT", 100.0, 0);
        assertThatThrownBy(() -> performanceService.addRecord("athlete", dto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addRecord_invalidReps_tooHigh_throwsException() {
        PerformanceRecordDto dto = new PerformanceRecordDto("SQUAT", 100.0, 37);
        assertThatThrownBy(() -> performanceService.addRecord("athlete", dto))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void addRecord_unknownExerciseType_throwsException() {
        PerformanceRecordDto dto = new PerformanceRecordDto("UNKNOWN_EX", 100.0, 5);
        assertThatThrownBy(() -> performanceService.addRecord("athlete", dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN_EX");
    }

    @Test
    void addRecord_withBodyweight_computesRatio() {
        PerformanceRecordDto dto = new PerformanceRecordDto("DEVELOPPE_COUCHE", 80.0, 1);
        when(performanceRecordRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        performanceService.addRecord("athlete", dto);

        verify(performanceRecordRepository).saveAndFlush(argThat(r ->
                r.getRatioPerformance() != null && r.getRatioPerformance() > 0
        ));
    }

    @Test
    void addRecord_noBodyweight_ratioIsNull() {
        user.setPoidsCorps(null);
        PerformanceRecordDto dto = new PerformanceRecordDto("DEVELOPPE_COUCHE", 80.0, 1);
        when(performanceRecordRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        performanceService.addRecord("athlete", dto);

        verify(performanceRecordRepository).saveAndFlush(argThat(r -> r.getRatioPerformance() == null));
    }

    // --- updateBodyweight ---

    @Test
    void updateBodyweight_updatesAndReturnsSummary() {
        PerformanceSummaryDto summary = performanceService.updateBodyweight("athlete", 85.0);

        verify(utilisateurRepository).save(argThat(u -> u.getPoidsCorps() == 85.0));
        assertThat(summary).isNotNull();
    }

    // --- getHistory ---

    @Test
    void getHistory_returnsEmptyList_whenNoRecords() {
        when(performanceRecordRepository.findByUtilisateurIdAndExerciseTypeOrderByRecordedAtDesc(any(), any()))
                .thenReturn(List.of());

        List<ExercisePerformanceDto> history = performanceService.getHistory("athlete", "SQUAT");

        assertThat(history).isEmpty();
    }

    @Test
    void getHistory_invalidExerciseType_throwsException() {
        assertThatThrownBy(() -> performanceService.getHistory("athlete", "INVALID"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getHistory_mapsRecordsCorrectly() {
        PerformanceRecord record = PerformanceRecord.builder()
                .exerciseType(ExerciseType.SQUAT)
                .poids(120.0)
                .nombreReps(5)
                .rm1Estime(135.0)
                .ratioPerformance(1.5)
                .poidsCorporel(80.0)
                .recordedAt(LocalDateTime.now())
                .build();

        when(performanceRecordRepository.findByUtilisateurIdAndExerciseTypeOrderByRecordedAtDesc(any(), any()))
                .thenReturn(List.of(record));

        List<ExercisePerformanceDto> history = performanceService.getHistory("athlete", "SQUAT");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getPoids()).isEqualTo(120.0);
        assertThat(history.get(0).getNombreReps()).isEqualTo(5);
    }
}
