package com.kronos.chiron.stats;

import com.kronos.chiron.performance.dto.PerformanceSummaryDto;
import com.kronos.chiron.seance.model.Degressif;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.exercice.model.ExerciceDefinition;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.exercice.model.TypeEquipement;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.nutrition.NutritionService;
import com.kronos.chiron.nutrition.olympusdb.OlympusNutritionDao;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.performance.service.PerformanceService;
import com.kronos.chiron.visbody.BodyCompositionRecordRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatsServiceTest {

    private static final String USERNAME = "kronos";

    @Mock
    private SeanceRepository seanceRepository;
    @Mock
    private PerformanceService performanceService;
    @Mock
    private NutritionService nutritionService;
    @Mock
    private OlympusNutritionDao olympusDao;
    @Mock
    private BodyCompositionRecordRepository bodyCompositionRepo;
    @Mock
    private UtilisateurRepository utilisateurRepository;

    @InjectMocks
    private StatsService statsService;

    private void givenSessions(Seance... sessions) {
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(USERNAME))
                .thenReturn(List.of(sessions));
    }

    private void givenTonnagePrefs(boolean halteresX2, boolean machineX2) {
        Utilisateur u = new Utilisateur();
        u.setPoidsHaltereParImplement(halteresX2);
        u.setPoidsMachineParCote(machineX2);
        when(utilisateurRepository.findByUsername(USERNAME)).thenReturn(Optional.of(u));
    }

    private void givenNoUser() {
        when(utilisateurRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
    }

    private void givenEmptySummary() {
        when(performanceService.getSummary(USERNAME)).thenReturn(PerformanceSummaryDto.builder().build());
    }

    private static Serie serie(double poids, int reps, Degressif... degressifs) {
        return Serie.builder().poids(poids).nombreReps(reps).degressifs(List.of(degressifs)).build();
    }

    private static Exercice exercice(String nom, boolean unilateral, TypeEquipement equipement, Serie... series) {
        ExerciceDefinition definition = equipement == null
                ? null
                : ExerciceDefinition.builder().typeEquipement(equipement).build();
        return Exercice.builder()
                .nom(nom)
                .unilateral(unilateral)
                .definition(definition)
                .series(List.of(series))
                .build();
    }

    private static Seance seance(LocalDateTime start, LocalDateTime end, Exercice... exercices) {
        return Seance.builder()
                .id(1L)
                .isModele(true)
                .startTime(start)
                .endTime(end)
                .exercices(List.of(exercices))
                .build();
    }

    private static LocalDate mondayOfCurrentWeek() {
        return LocalDate.now().with(WeekFields.ISO.dayOfWeek(), 1);
    }

    @Test
    void getOverview_noSessions_returnsZeroedCounters() {
        givenSessions();
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.seances30()).isZero();
        assertThat(overview.seancesTotal()).isZero();
        assertThat(overview.tonnageSemaine()).isZero();
        assertThat(overview.streakSemaines()).isZero();
        assertThat(overview.dureeMoyenneMin()).isNull();
    }

    @Test
    void getOverview_sessionOlderThanThirtyDays_countedInTotalButNotInSeances30() {
        LocalDateTime old = LocalDateTime.now().minusDays(45);
        givenSessions(seance(old, old.plusHours(1)));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.seances30()).isZero();
        assertThat(overview.seancesTotal()).isEqualTo(1);
    }

    @Test
    void getOverview_sessionWithinThirtyDays_countedInSeances30() {
        LocalDateTime recent = LocalDateTime.now().minusDays(3);
        givenSessions(seance(recent, recent.plusHours(1)));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.seances30()).isEqualTo(1);
    }

    @Test
    void getOverview_sessionWithoutStartTime_excludedFromSeances30() {
        givenSessions(seance(null, null, exercice("Squat", false, null, serie(100, 5))));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.seances30()).isZero();
        assertThat(overview.seancesTotal()).isEqualTo(1);
        assertThat(overview.tonnageSemaine()).isZero();
    }

    @Test
    void getOverview_currentWeekSession_tonnageCountsPoidsTimesReps() {
        LocalDateTime start = mondayOfCurrentWeek().atTime(10, 0);
        givenSessions(seance(start, start.plusHours(1), exercice("Squat", false, null, serie(100, 5))));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.tonnageSemaine()).isEqualTo(500.0);
    }

    @Test
    void getOverview_degressifs_addedToTonnage() {
        LocalDateTime start = mondayOfCurrentWeek().atTime(10, 0);
        Degressif degressif = Degressif.builder().poids(50).nombreReps(4).build();
        givenSessions(seance(start, start.plusHours(1),
                exercice("Squat", false, null, serie(100, 5, degressif))));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.tonnageSemaine()).isEqualTo(700.0);
    }

    @Test
    void getOverview_unilateralExercise_doublesTonnage() {
        LocalDateTime start = mondayOfCurrentWeek().atTime(10, 0);
        givenSessions(seance(start, start.plusHours(1), exercice("Fentes", true, null, serie(40, 10))));
        givenTonnagePrefs(false, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.tonnageSemaine()).isEqualTo(800.0);
    }

    @Test
    void getOverview_halteresWithPreferenceEnabled_doublesTonnage() {
        LocalDateTime start = mondayOfCurrentWeek().atTime(10, 0);
        givenSessions(seance(start, start.plusHours(1),
                exercice("Curl", false, TypeEquipement.HALTERES, serie(20, 10))));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.tonnageSemaine()).isEqualTo(400.0);
    }

    @Test
    void getOverview_halteresWithPreferenceDisabled_doesNotDoubleTonnage() {
        LocalDateTime start = mondayOfCurrentWeek().atTime(10, 0);
        givenSessions(seance(start, start.plusHours(1),
                exercice("Curl", false, TypeEquipement.HALTERES, serie(20, 10))));
        givenTonnagePrefs(false, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.tonnageSemaine()).isEqualTo(200.0);
    }

    @Test
    void getOverview_unilateralHalteres_doublesOnlyOnce() {
        LocalDateTime start = mondayOfCurrentWeek().atTime(10, 0);
        givenSessions(seance(start, start.plusHours(1),
                exercice("Curl unilatéral", true, TypeEquipement.HALTERES, serie(20, 10))));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.tonnageSemaine()).isEqualTo(400.0);
    }

    @Test
    void getOverview_unknownUser_fallsBackToDefaultTonnagePrefs() {
        LocalDateTime start = mondayOfCurrentWeek().atTime(10, 0);
        givenSessions(seance(start, start.plusHours(1),
                exercice("Curl", false, TypeEquipement.HALTERES, serie(20, 10))));
        givenNoUser();
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.tonnageSemaine()).isEqualTo(400.0);
    }

    @Test
    void getOverview_machineWithPreferenceEnabled_doublesTonnage() {
        LocalDateTime start = mondayOfCurrentWeek().atTime(10, 0);
        givenSessions(seance(start, start.plusHours(1),
                exercice("Presse", false, TypeEquipement.MACHINE, serie(80, 10))));
        givenTonnagePrefs(false, true);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.tonnageSemaine()).isEqualTo(1600.0);
    }

    @Test
    void getOverview_sessionInPreviousWeek_excludedFromWeeklyTonnage() {
        LocalDateTime lastWeek = mondayOfCurrentWeek().minusWeeks(1).atTime(10, 0);
        givenSessions(seance(lastWeek, lastWeek.plusHours(1),
                exercice("Squat", false, null, serie(100, 5))));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.tonnageSemaine()).isZero();
    }

    @Test
    void getOverview_consecutiveWeeks_streakCountsThem() {
        LocalDate monday = mondayOfCurrentWeek();
        givenSessions(
                seance(monday.atTime(10, 0), monday.atTime(11, 0)),
                seance(monday.minusWeeks(1).atTime(10, 0), monday.minusWeeks(1).atTime(11, 0)),
                seance(monday.minusWeeks(2).atTime(10, 0), monday.minusWeeks(2).atTime(11, 0)));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.streakSemaines()).isEqualTo(3);
    }

    @Test
    void getOverview_gapInWeeks_streakStopsAtTheGap() {
        LocalDate monday = mondayOfCurrentWeek();
        givenSessions(
                seance(monday.atTime(10, 0), monday.atTime(11, 0)),
                seance(monday.minusWeeks(3).atTime(10, 0), monday.minusWeeks(3).atTime(11, 0)));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.streakSemaines()).isEqualTo(1);
    }

    @Test
    void getOverview_noSessionThisWeek_streakIsZero() {
        LocalDate lastWeek = mondayOfCurrentWeek().minusWeeks(1);
        givenSessions(seance(lastWeek.atTime(10, 0), lastWeek.atTime(11, 0)));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.streakSemaines()).isZero();
    }

    @Test
    void getOverview_sessionsWithDuration_averagesThem() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        givenSessions(
                seance(start, start.plusMinutes(60)),
                seance(start, start.plusMinutes(90)));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.dureeMoyenneMin()).isEqualTo(75.0);
    }

    @Test
    void getOverview_sessionWithoutEndTime_excludedFromAverageDuration() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        givenSessions(
                seance(start, start.plusMinutes(60)),
                seance(start, null));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.dureeMoyenneMin()).isEqualTo(60.0);
    }

    @Test
    void getOverview_endTimeBeforeStartTime_excludedFromAverageDuration() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        givenSessions(seance(start, start.minusMinutes(30)));
        givenTonnagePrefs(true, false);
        givenEmptySummary();

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.dureeMoyenneMin()).isNull();
    }

    @Test
    void getOverview_forwardsPerformanceSummaryFields() {
        givenSessions();
        givenTonnagePrefs(true, false);
        when(performanceService.getSummary(USERNAME)).thenReturn(PerformanceSummaryDto.builder()
                .poidsCorps(82.5)
                .overallTier("Or")
                .overallTierLevel(3)
                .overallTierCategorie("Intermédiaire")
                .build());

        StatsOverviewDto overview = statsService.getOverview(USERNAME);

        assertThat(overview.poidsCorps()).isEqualTo(82.5);
        assertThat(overview.tier()).isEqualTo("Or");
        assertThat(overview.tierLevel()).isEqualTo(3);
        assertThat(overview.tierCategorie()).isEqualTo("Intermédiaire");
    }

    @Test
    void getWeeklyVolume_returnsOnePointPerRequestedWeek() {
        givenSessions();
        givenTonnagePrefs(true, false);

        List<WeeklyVolumePointDto> points = statsService.getWeeklyVolume(USERNAME, 8);

        assertThat(points).hasSize(8);
    }

    @Test
    void getWeeklyVolume_pointsAreOrderedOldestFirstEndingOnCurrentWeek() {
        givenSessions();
        givenTonnagePrefs(true, false);

        List<WeeklyVolumePointDto> points = statsService.getWeeklyVolume(USERNAME, 4);

        assertThat(points.get(0).semaine()).isEqualTo(mondayOfCurrentWeek().minusWeeks(3));
        assertThat(points.get(3).semaine()).isEqualTo(mondayOfCurrentWeek());
    }

    @Test
    void getWeeklyVolume_zeroWeeks_isClampedToOne() {
        givenSessions();
        givenTonnagePrefs(true, false);

        assertThat(statsService.getWeeklyVolume(USERNAME, 0)).hasSize(1);
    }

    @Test
    void getWeeklyVolume_negativeWeeks_isClampedToOne() {
        givenSessions();
        givenTonnagePrefs(true, false);

        assertThat(statsService.getWeeklyVolume(USERNAME, -5)).hasSize(1);
    }

    @Test
    void getWeeklyVolume_moreThanFiftyTwoWeeks_isClampedToFiftyTwo() {
        givenSessions();
        givenTonnagePrefs(true, false);

        assertThat(statsService.getWeeklyVolume(USERNAME, 200)).hasSize(52);
    }

    @Test
    void getWeeklyVolume_currentWeekSession_aggregatesTonnageSeancesAndSeries() {
        LocalDateTime start = mondayOfCurrentWeek().atTime(10, 0);
        givenSessions(seance(start, start.plusMinutes(60),
                exercice("Squat", false, null, serie(100, 5), serie(100, 5))));
        givenTonnagePrefs(true, false);

        List<WeeklyVolumePointDto> points = statsService.getWeeklyVolume(USERNAME, 1);

        assertThat(points).hasSize(1);
        assertThat(points.get(0).tonnage()).isEqualTo(1000.0);
        assertThat(points.get(0).nbSeances()).isEqualTo(1);
        assertThat(points.get(0).nbSeries()).isEqualTo(2);
        assertThat(points.get(0).dureeMoyenneMin()).isEqualTo(60.0);
    }

    @Test
    void getWeeklyVolume_weekWithoutSession_hasZeroedPointAndNullDuration() {
        givenSessions();
        givenTonnagePrefs(true, false);

        WeeklyVolumePointDto point = statsService.getWeeklyVolume(USERNAME, 1).get(0);

        assertThat(point.tonnage()).isZero();
        assertThat(point.nbSeances()).isZero();
        assertThat(point.nbSeries()).isZero();
        assertThat(point.dureeMoyenneMin()).isNull();
    }

    @Test
    void getExerciseList_noSessions_returnsEmptyList() {
        givenSessions();

        assertThat(statsService.getExerciseList(USERNAME)).isEmpty();
    }

    @Test
    void getExerciseList_sameExerciseInTwoSessions_isReturnedOnce() {
        LocalDateTime start = LocalDateTime.now().minusDays(2);
        Seance first = seance(start, start.plusHours(1), exercice("Squat", false, null, serie(100, 5)));
        Seance second = seance(start.minusDays(7), start.minusDays(7).plusHours(1),
                exercice("Squat", false, null, serie(100, 5)));
        second.setId(2L);
        givenSessions(first, second);

        List<ExerciseListItemDto> items = statsService.getExerciseList(USERNAME);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).nom()).isEqualTo("Squat");
    }

    @Test
    void getBodyweight_userNotLinkedToOlympus_returnsNotLinked() {
        when(nutritionService.getValidToken(USERNAME))
                .thenThrow(new NutritionService.NotLinkedException());

        BodyweightStatsDto stats = statsService.getBodyweight(USERNAME, 30);

        assertThat(stats.linked()).isFalse();
        assertThat(stats.points()).isEmpty();
    }

    @Test
    void getBodyweight_olympusTokenExpired_returnsNotLinked() {
        when(nutritionService.getValidToken(USERNAME))
                .thenThrow(new NutritionService.ExpiredException());

        assertThat(statsService.getBodyweight(USERNAME, 30).linked()).isFalse();
    }

    @Test
    void getBodyweight_olympusUserNotResolved_returnsNotLinked() {
        when(nutritionService.getValidToken(USERNAME)).thenReturn("token");
        when(olympusDao.resolveUserId("token")).thenReturn(Optional.empty());

        assertThat(statsService.getBodyweight(USERNAME, 30).linked()).isFalse();
    }

    @Test
    void getNutrition_userNotLinkedToOlympus_returnsNotLinked() {
        when(nutritionService.getValidToken(USERNAME))
                .thenThrow(new NutritionService.NotLinkedException());

        NutritionStatsDto stats = statsService.getNutrition(USERNAME, 30);

        assertThat(stats.linked()).isFalse();
        assertThat(stats.jours()).isEmpty();
        assertThat(stats.moyKcal()).isNull();
    }

    @Test
    void getNutrition_daysWithoutData_averagesStayNull() {
        when(nutritionService.getValidToken(USERNAME)).thenReturn("token");
        when(olympusDao.resolveUserId("token")).thenReturn(Optional.of(7L));
        when(olympusDao.dailyNutrition(eq(7L), any(), any())).thenReturn(List.of());

        NutritionStatsDto stats = statsService.getNutrition(USERNAME, 30);

        assertThat(stats.linked()).isTrue();
        assertThat(stats.moyKcal()).isNull();
        assertThat(stats.moyTargetKcal()).isNull();
    }

    @Test
    void getBodyComposition_unknownUser_returnsEmpty() {
        givenNoUser();

        BodyCompositionStatsDto stats = statsService.getBodyComposition(USERNAME, 90);

        assertThat(stats.hasData()).isFalse();
        assertThat(stats.points()).isEmpty();
    }

    @Test
    void getBodyComposition_noRecords_returnsEmpty() {
        Utilisateur user = new Utilisateur();
        when(utilisateurRepository.findByUsername(USERNAME)).thenReturn(Optional.of(user));
        when(bodyCompositionRepo.findByUtilisateurAndMesureLeAfterOrderByMesureLeAsc(eq(user), any()))
                .thenReturn(List.of());

        assertThat(statsService.getBodyComposition(USERNAME, 90).hasData()).isFalse();
    }
}
