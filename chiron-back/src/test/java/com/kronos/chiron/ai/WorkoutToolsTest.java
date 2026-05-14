package com.kronos.chiron.ai;

import com.kronos.chiron.entity.*;
import com.kronos.chiron.repository.ExerciceRepository;
import com.kronos.chiron.repository.SeanceRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkoutToolsTest {

    @Mock private SeanceRepository seanceRepository;
    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private ExerciceRepository exerciceRepository;

    @InjectMocks
    private WorkoutTools workoutTools;

    private Utilisateur user;

    @BeforeEach
    void setUp() {
        user = Utilisateur.builder()
                .id(1L)
                .username("athlete")
                .role(Role.USER)
                .isPublic(true)
                .build();
        when(utilisateurRepository.findById(1L)).thenReturn(Optional.of(user));
        when(utilisateurRepository.findByUsername("athlete")).thenReturn(Optional.of(user));
        when(seanceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // --- getCurrentDate ---

    @Test
    void getCurrentDate_returnsNonNullString() {
        String date = workoutTools.getCurrentDate();
        assertThat(date).isNotBlank().startsWith("Nous sommes le");
    }

    // --- startSession ---

    @Test
    void startSession_createsNewSeance() {
        when(seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.empty());

        String result = workoutTools.startSession("1", "Push Day");

        assertThat(result).contains("Push Day");
        verify(seanceRepository).save(argThat(s ->
                s.getTitre().equals("Push Day") &&
                s.getUtilisateur().equals(user) &&
                s.isModele()
        ));
    }

    @Test
    void startSession_closesExistingActiveSeance() {
        Seance active = new Seance();
        active.setStartTime(LocalDateTime.now().minusHours(1));
        when(seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.of(active));

        workoutTools.startSession("1", "New Session");

        assertThat(active.getEndTime()).isNotNull();
        verify(seanceRepository, atLeast(1)).save(any());
    }

    // --- startExercise ---

    @Test
    void startExercise_activeSeanceExists_addsExercice() {
        Seance activeSeance = new Seance();
        activeSeance.setId(10L);
        activeSeance.setUtilisateur(user);
        when(seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.of(activeSeance));

        String result = workoutTools.startExercise("1", "Bench Press");

        assertThat(result).contains("Bench Press");
        assertThat(activeSeance.getExercices()).hasSize(1);
        assertThat(activeSeance.getExercices().get(0).getNom()).isEqualTo("Bench Press");
    }

    @Test
    void startExercise_noActiveSeance_returnsError() {
        when(seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.empty());

        String result = workoutTools.startExercise("1", "Squat");

        assertThat(result).containsIgnoringCase("ERREUR");
    }

    // --- addSet ---

    @Test
    void addSet_activeSeanceWithExercice_addsSerie() {
        Seance activeSeance = new Seance();
        Exercice exercice = new Exercice();
        exercice.setNom("Bench");
        activeSeance.addExercice(exercice);

        when(seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.of(activeSeance));

        String result = workoutTools.addSet("1", 100.0, 5, "good set");

        assertThat(result).contains("5x100");
        assertThat(exercice.getSeries()).hasSize(1);
        assertThat(exercice.getSeries().get(0).getPoids()).isEqualTo(100.0);
        assertThat(exercice.getSeries().get(0).getNombreReps()).isEqualTo(5);
    }

    @Test
    void addSet_noActiveSeance_returnsError() {
        when(seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.empty());

        String result = workoutTools.addSet("1", 80.0, 8, null);

        assertThat(result).containsIgnoringCase("ERREUR");
    }

    @Test
    void addSet_activeSeanceNoExercice_returnsError() {
        Seance activeSeance = new Seance();
        when(seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.of(activeSeance));

        String result = workoutTools.addSet("1", 80.0, 8, null);

        assertThat(result).containsIgnoringCase("ERREUR");
    }

    // --- endSession ---

    @Test
    void endSession_activeSeance_closesIt() {
        Seance activeSeance = new Seance();
        activeSeance.setId(5L);
        Exercice exo = new Exercice();
        exo.setNom("Squat");
        activeSeance.addExercice(exo);

        when(seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.of(activeSeance));

        String result = workoutTools.endSession("1");

        assertThat(result).containsIgnoringCase("terminée");
        assertThat(activeSeance.getEndTime()).isNotNull();
    }

    @Test
    void endSession_noActiveSeance_returnsMessage() {
        when(seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.empty());

        String result = workoutTools.endSession("1");

        assertThat(result).isNotBlank();
    }

    @Test
    void endSession_closesLastExerciceIfOpen() {
        Seance activeSeance = new Seance();
        Exercice exo = new Exercice();
        exo.setNom("Deadlift");
        activeSeance.addExercice(exo);

        when(seanceRepository.findFirstByUtilisateurIdAndEndTimeIsNullOrderByStartTimeDesc(1L))
                .thenReturn(Optional.of(activeSeance));

        workoutTools.endSession("1");

        assertThat(exo.getEndTime()).isNotNull();
    }

    // --- getUserProgrammes ---

    @Test
    void getUserProgrammes_ownProgrammes_returnsList() {
        Seance prog = new Seance();
        prog.setTitre("My Programme");
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of(prog));

        String result = workoutTools.getUserProgrammes("1", null);

        assertThat(result).contains("My Programme");
    }

    @Test
    void getUserProgrammes_noProgrammes_returnsEmptyMessage() {
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of());

        String result = workoutTools.getUserProgrammes("1", null);

        assertThat(result).containsIgnoringCase("aucun");
    }

    @Test
    void getUserProgrammes_privateTargetUser_returnsDeniedMessage() {
        Utilisateur privateUser = Utilisateur.builder()
                .id(2L).username("priv").isPublic(false).role(Role.USER).build();
        when(utilisateurRepository.findByUsername("priv")).thenReturn(Optional.of(privateUser));

        String result = workoutTools.getUserProgrammes("1", "priv");

        assertThat(result).containsIgnoringCase("privé");
    }

    @Test
    void getUserProgrammes_unknownTarget_returnsNotFoundMessage() {
        when(utilisateurRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        String result = workoutTools.getUserProgrammes("1", "ghost");

        assertThat(result).containsIgnoringCase("introuvable");
    }

    // --- getUserHistory ---

    @Test
    void getUserHistory_ownHistory_returnsSessions() {
        Seance session = new Seance();
        session.setTitre("Back Day");
        session.setStartTime(LocalDateTime.now());
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of(session));

        String result = workoutTools.getUserHistory("1", null);

        assertThat(result).contains("Back Day");
    }

    @Test
    void getUserHistory_noHistory_returnsEmptyMessage() {
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of());

        String result = workoutTools.getUserHistory("1", null);

        assertThat(result).containsIgnoringCase("aucune");
    }

    @Test
    void getUserHistory_privateTarget_returnsDeniedMessage() {
        Utilisateur priv = Utilisateur.builder()
                .id(3L).username("priv2").isPublic(false).role(Role.USER).build();
        when(utilisateurRepository.findByUsername("priv2")).thenReturn(Optional.of(priv));

        String result = workoutTools.getUserHistory("1", "priv2");

        assertThat(result).containsIgnoringCase("privé");
    }

    // --- getLastExercisePerformance ---

    @Test
    void getLastExercisePerformance_noHistory_returnsNotFoundMessage() {
        when(exerciceRepository.findFirstHistoricExercise(1L, "Squat"))
                .thenReturn(Optional.empty());

        String result = workoutTools.getLastExercisePerformance("1", "Squat");

        assertThat(result).containsIgnoringCase("jamais");
    }

    @Test
    void getLastExercisePerformance_withHistory_returnsSeries() {
        Serie serie = new Serie();
        serie.setPoids(120.0);
        serie.setNombreReps(3);

        Exercice exo = new Exercice();
        exo.setNom("Squat");
        exo.setStartTime(LocalDateTime.now().minusDays(3));
        exo.addSerie(serie);

        when(exerciceRepository.findFirstHistoricExercise(1L, "Squat"))
                .thenReturn(Optional.of(exo));

        String result = workoutTools.getLastExercisePerformance("1", "Squat");

        assertThat(result).contains("3").contains("120");
    }

    @Test
    void getLastExercisePerformance_exerciceNoSeries_returnsNoSeriesMessage() {
        Exercice exo = new Exercice();
        exo.setNom("Bench");
        exo.setStartTime(LocalDateTime.now().minusDays(1));

        when(exerciceRepository.findFirstHistoricExercise(1L, "Bench"))
                .thenReturn(Optional.of(exo));

        String result = workoutTools.getLastExercisePerformance("1", "Bench");

        assertThat(result).containsIgnoringCase("aucune");
    }

    // --- searchAllProfiles (admin only) ---

    @Test
    void searchAllProfiles_nonAdmin_returnsAccessDenied() {
        String result = workoutTools.searchAllProfiles("1", "alice");
        assertThat(result).containsIgnoringCase("refusé");
    }

    @Test
    void searchAllProfiles_admin_returnsResults() {
        Utilisateur admin = Utilisateur.builder()
                .id(10L).username("admin").role(Role.ADMIN).build();
        when(utilisateurRepository.findById(10L)).thenReturn(Optional.of(admin));
        when(utilisateurRepository.findByUsernameContainingIgnoreCase("ali")).thenReturn(
                List.of(Utilisateur.builder().id(2L).username("alice").role(Role.USER).isPublic(true).build()));

        String result = workoutTools.searchAllProfiles("10", "ali");

        assertThat(result).contains("alice");
    }

    // --- createProgramModel ---

    @Test
    void createProgramModel_savesTemplate() {
        String result = workoutTools.createProgramModel("1", "My Template");

        assertThat(result).containsIgnoringCase("My Template");
        verify(seanceRepository).save(argThat(s ->
                s.getTitre().equals("My Template") && !s.isModele()
        ));
    }

    // --- getWorkoutSummaryByDate ---

    @Test
    void getWorkoutSummaryByDate_noMatchingDate_returnsNotFound() {
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of());

        String result = workoutTools.getWorkoutSummaryByDate("1", "2025-01-01");

        assertThat(result).containsIgnoringCase("aucune");
    }

    @Test
    void getWorkoutSummaryByDate_matchingDate_returnsSummary() {
        Seance session = new Seance();
        session.setTitre("Back Workout");
        session.setStartTime(LocalDateTime.of(2025, 1, 15, 10, 0));
        Exercice exo = new Exercice();
        exo.setNom("Pull-up");
        Serie serie = new Serie();
        serie.setPoids(0.0);
        serie.setNombreReps(10);
        exo.addSerie(serie);
        session.addExercice(exo);

        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of(session));

        String result = workoutTools.getWorkoutSummaryByDate("1", "2025-01-15");

        assertThat(result).contains("Back Workout").contains("Pull-up");
    }

    // --- getProgrammeDetails ---

    @Test
    void getProgrammeDetails_matchingName_returnsExercises() {
        Seance prog = new Seance();
        prog.setTitre("Push Day");
        Exercice exo = new Exercice();
        exo.setNom("Bench Press");
        Serie serie = new Serie();
        serie.setPoids(100.0);
        serie.setNombreReps(5);
        exo.addSerie(serie);
        prog.addExercice(exo);

        when(seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of(prog));

        String result = workoutTools.getProgrammeDetails("1", "Push");

        assertThat(result).contains("Push Day").contains("Bench Press").contains("5 reps @ 100.0kg");
    }

    @Test
    void getProgrammeDetails_noMatch_returnsNotFound() {
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of());

        String result = workoutTools.getProgrammeDetails("1", "Unknown");

        assertThat(result).containsIgnoringCase("aucun");
    }

    @Test
    void getProgrammeDetails_emptyProgramme_reportsNoExercises() {
        Seance prog = new Seance();
        prog.setTitre("Empty Programme");

        when(seanceRepository.findByUtilisateurUsernameAndIsModeleFalseOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of(prog));

        String result = workoutTools.getProgrammeDetails("1", "Empty");

        assertThat(result).contains("Empty Programme").containsIgnoringCase("aucun");
    }

    // --- getSessionDetails ---

    @Test
    void getSessionDetails_matchingTitle_returnsFullDetails() {
        Seance session = new Seance();
        session.setTitre("Leg Day");
        session.setStartTime(LocalDateTime.of(2025, 3, 10, 9, 30));
        Exercice exo = new Exercice();
        exo.setNom("Squat");
        Serie serie = new Serie();
        serie.setPoids(120.0);
        serie.setNombreReps(3);
        serie.setCommentaire("bon set");
        exo.addSerie(serie);
        session.addExercice(exo);

        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of(session));

        String result = workoutTools.getSessionDetails("1", "Leg");

        assertThat(result).contains("Leg Day").contains("Squat").contains("3 reps @ 120.0kg").contains("bon set");
    }

    @Test
    void getSessionDetails_noMatch_returnsNotFound() {
        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of());

        String result = workoutTools.getSessionDetails("1", "Ghost Session");

        assertThat(result).containsIgnoringCase("aucune");
    }

    @Test
    void getSessionDetails_sessionWithNoExercises_reportsEmpty() {
        Seance session = new Seance();
        session.setTitre("Rest Day");
        session.setStartTime(LocalDateTime.now());

        when(seanceRepository.findByUtilisateurUsernameAndIsModeleTrueOrderByStartTimeDesc(user.getUsername()))
                .thenReturn(List.of(session));

        String result = workoutTools.getSessionDetails("1", "Rest");

        assertThat(result).contains("Rest Day").containsIgnoringCase("aucun");
    }

    // --- getFullExerciseHistory ---

    @Test
    void getFullExerciseHistory_withMultipleSessions_returnsAll() {
        Exercice exo1 = new Exercice();
        exo1.setNom("Deadlift");
        exo1.setStartTime(LocalDateTime.now().minusDays(7));
        Serie s1 = new Serie();
        s1.setPoids(150.0);
        s1.setNombreReps(5);
        exo1.addSerie(s1);

        Exercice exo2 = new Exercice();
        exo2.setNom("Deadlift");
        exo2.setStartTime(LocalDateTime.now().minusDays(14));
        Serie s2 = new Serie();
        s2.setPoids(140.0);
        s2.setNombreReps(5);
        exo2.addSerie(s2);

        when(exerciceRepository.findAllHistoricExercises(1L, "Deadlift")).thenReturn(List.of(exo1, exo2));

        String result = workoutTools.getFullExerciseHistory("1", "Deadlift");

        assertThat(result).contains("Deadlift").contains("150.0kg").contains("140.0kg").contains("2 séance(s)");
    }

    @Test
    void getFullExerciseHistory_noHistory_returnsNotFound() {
        when(exerciceRepository.findAllHistoricExercises(1L, "OHP")).thenReturn(List.of());

        String result = workoutTools.getFullExerciseHistory("1", "OHP");

        assertThat(result).containsIgnoringCase("jamais");
    }

    // --- getPersonalRecord ---

    @Test
    void getPersonalRecord_withSets_returnsBestSet() {
        Exercice exo = new Exercice();
        exo.setNom("Bench Press");
        exo.setStartTime(LocalDateTime.of(2025, 2, 1, 10, 0));
        Serie light = new Serie();
        light.setPoids(80.0);
        light.setNombreReps(10);
        Serie heavy = new Serie();
        heavy.setPoids(120.0);
        heavy.setNombreReps(1);
        exo.addSerie(light);
        exo.addSerie(heavy);

        when(exerciceRepository.findAllHistoricExercises(1L, "Bench Press")).thenReturn(List.of(exo));

        String result = workoutTools.getPersonalRecord("1", "Bench Press");

        assertThat(result).contains("Bench Press").contains("1RM").contains("kg");
    }

    @Test
    void getPersonalRecord_noHistory_returnsNotFound() {
        when(exerciceRepository.findAllHistoricExercises(1L, "Row")).thenReturn(List.of());

        String result = workoutTools.getPersonalRecord("1", "Row");

        assertThat(result).containsIgnoringCase("jamais");
    }

    @Test
    void getPersonalRecord_exercisesWithNoSeries_returnsNoSeriesMessage() {
        Exercice exo = new Exercice();
        exo.setNom("Pull-up");
        exo.setStartTime(LocalDateTime.now());

        when(exerciceRepository.findAllHistoricExercises(1L, "Pull-up")).thenReturn(List.of(exo));

        String result = workoutTools.getPersonalRecord("1", "Pull-up");

        assertThat(result).containsIgnoringCase("aucune");
    }
}
