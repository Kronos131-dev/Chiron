package com.kronos.chiron.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kronos.chiron.entity.*;
import com.kronos.chiron.repository.ExerciceDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExerciceDataImporterTest {

    @Mock
    private ExerciceDefinitionRepository repository;

    @InjectMocks
    private ExerciceDataImporter importer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(importer, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(importer, "uploadsDir", tempDir.toString());
    }

    // ── Static mapping tables ─────────────────────────────────────────────────

    @Test
    void levelMap_coversAllLevels() {
        assertThat(ExerciceDataImporter.LEVEL_MAP).containsKeys("beginner", "intermediate", "expert");
        assertThat(ExerciceDataImporter.LEVEL_MAP.get("beginner")).isEqualTo(NiveauDifficulte.DEBUTANT);
        assertThat(ExerciceDataImporter.LEVEL_MAP.get("intermediate")).isEqualTo(NiveauDifficulte.INTERMEDIAIRE);
        assertThat(ExerciceDataImporter.LEVEL_MAP.get("expert")).isEqualTo(NiveauDifficulte.AVANCE);
    }

    @Test
    void equipmentMap_bodyOnly_mapsToPoidsDuCorps() {
        assertThat(ExerciceDataImporter.EQUIPMENT_MAP.get("body only")).isEqualTo(TypeEquipement.POIDS_DU_CORPS);
    }

    @Test
    void equipmentMap_barbell_mapsToBarre() {
        assertThat(ExerciceDataImporter.EQUIPMENT_MAP.get("barbell")).isEqualTo(TypeEquipement.BARRE);
    }

    @Test
    void equipmentMap_dumbbell_mapsToHalteres() {
        assertThat(ExerciceDataImporter.EQUIPMENT_MAP.get("dumbbell")).isEqualTo(TypeEquipement.HALTERES);
    }

    @Test
    void equipmentMap_cable_mapsToPoulie() {
        assertThat(ExerciceDataImporter.EQUIPMENT_MAP.get("cable")).isEqualTo(TypeEquipement.POULIE);
    }

    @Test
    void muscleMap_chest_mapsToPectoraux() {
        assertThat(ExerciceDataImporter.MUSCLE_MAP.get("chest")).isEqualTo(MuscleGroup.PECTORAUX);
    }

    @Test
    void muscleMap_quadriceps_mapsToQuadriceps() {
        assertThat(ExerciceDataImporter.MUSCLE_MAP.get("quadriceps")).isEqualTo(MuscleGroup.QUADRICEPS);
    }

    @Test
    void muscleMap_lats_mapsToDos() {
        assertThat(ExerciceDataImporter.MUSCLE_MAP.get("lats")).isEqualTo(MuscleGroup.DOS);
    }

    @Test
    void frNames_squatHasFrenchTranslation() {
        assertThat(ExerciceDataImporter.FR_NAMES).containsKey("barbell squat");
        assertThat(ExerciceDataImporter.FR_NAMES.get("barbell squat")).isEqualTo("Squat barre");
    }

    @Test
    void frNames_benchPressMediumGripHasFrenchTranslation() {
        assertThat(ExerciceDataImporter.FR_NAMES).containsKey("barbell bench press - medium grip");
        assertThat(ExerciceDataImporter.FR_NAMES.get("barbell bench press - medium grip"))
                .isEqualTo("Développé couché barre prise moyenne");
    }

    @Test
    void frNames_plankHasFrenchTranslation() {
        assertThat(ExerciceDataImporter.FR_NAMES.get("plank")).isEqualTo("Gainage");
    }

    // ── importFromFile ────────────────────────────────────────────────────────

    @Test
    void importFromFile_emptyArray_importsZero() throws Exception {
        Path json = tempDir.resolve("empty.json");
        Files.writeString(json, "[]");

        int count = importer.importFromFile(json, null);

        assertThat(count).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void importFromFile_notArray_throwsIllegalArgument() throws Exception {
        Path json = tempDir.resolve("bad.json");
        Files.writeString(json, "{\"key\": \"value\"}");

        assertThatThrownBy(() -> importer.importFromFile(json, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void importFromFile_singleExercise_savesEntity() throws Exception {
        Path json = tempDir.resolve("exercises.json");
        Files.writeString(json, """
                [
                  {
                    "id": "Barbell_Squat",
                    "name": "Barbell Squat",
                    "level": "intermediate",
                    "equipment": "barbell",
                    "primaryMuscles": ["quadriceps"],
                    "secondaryMuscles": ["glutes", "hamstrings"],
                    "instructions": ["Stand with bar on back.", "Descend until thighs parallel."],
                    "images": []
                  }
                ]
                """);

        when(repository.findByExternalId("Barbell_Squat")).thenReturn(Optional.empty());

        int count = importer.importFromFile(json, null);

        assertThat(count).isEqualTo(1);

        ArgumentCaptor<ExerciceDefinition> captor = ArgumentCaptor.forClass(ExerciceDefinition.class);
        verify(repository).save(captor.capture());

        ExerciceDefinition saved = captor.getValue();
        assertThat(saved.getExternalId()).isEqualTo("Barbell_Squat");
        assertThat(saved.getNomEn()).isEqualTo("Barbell Squat");
        assertThat(saved.getNomFr()).isEqualTo("Squat barre");
        assertThat(saved.getMusclePrincipal()).isEqualTo(MuscleGroup.QUADRICEPS);
        assertThat(saved.getTypeEquipement()).isEqualTo(TypeEquipement.BARRE);
        assertThat(saved.getDifficulte()).isEqualTo(NiveauDifficulte.INTERMEDIAIRE);
        assertThat(saved.getMusclesSecondaires()).containsExactlyInAnyOrder(MuscleGroup.FESSIERS, MuscleGroup.ISCHIO_JAMBIERS);
        assertThat(saved.getDescriptionEn()).contains("Stand with bar on back.");
    }

    @Test
    void importFromFile_idempotent_skipsExisting() throws Exception {
        Path json = tempDir.resolve("exercises.json");
        Files.writeString(json, """
                [
                  {
                    "id": "Barbell_Squat",
                    "name": "Barbell Squat",
                    "level": "intermediate",
                    "equipment": "barbell",
                    "primaryMuscles": ["quadriceps"],
                    "secondaryMuscles": [],
                    "instructions": [],
                    "images": []
                  }
                ]
                """);

        when(repository.findByExternalId("Barbell_Squat"))
                .thenReturn(Optional.of(ExerciceDefinition.builder().externalId("Barbell_Squat").nomEn("x").build()));

        int count = importer.importFromFile(json, null);

        assertThat(count).isZero();
        verify(repository, never()).save(any());
    }

    @Test
    void importFromFile_missingName_skipsExercise() throws Exception {
        Path json = tempDir.resolve("exercises.json");
        Files.writeString(json, """
                [
                  {
                    "id": "No_Name",
                    "name": "",
                    "level": "beginner",
                    "equipment": "body only",
                    "primaryMuscles": [],
                    "secondaryMuscles": [],
                    "instructions": [],
                    "images": []
                  }
                ]
                """);

        int count = importer.importFromFile(json, null);

        assertThat(count).isZero();
    }

    @Test
    void importFromFile_unknownFrName_setsNomFrNull() throws Exception {
        Path json = tempDir.resolve("exercises.json");
        Files.writeString(json, """
                [
                  {
                    "id": "XYZZY_Weird_Exercise",
                    "name": "XYZZY Weird Exercise",
                    "level": "beginner",
                    "equipment": "body only",
                    "primaryMuscles": ["chest"],
                    "secondaryMuscles": [],
                    "instructions": [],
                    "images": []
                  }
                ]
                """);

        when(repository.findByExternalId("XYZZY_Weird_Exercise")).thenReturn(Optional.empty());

        importer.importFromFile(json, null);

        ArgumentCaptor<ExerciceDefinition> captor = ArgumentCaptor.forClass(ExerciceDefinition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getNomFr()).isNull();
    }

    @Test
    void importFromFile_unknownEquipment_fallsBackToAutre() throws Exception {
        Path json = tempDir.resolve("exercises.json");
        Files.writeString(json, """
                [
                  {
                    "id": "Test_Exercise",
                    "name": "Test Exercise",
                    "level": "beginner",
                    "equipment": "weird_unknown_equipment",
                    "primaryMuscles": ["chest"],
                    "secondaryMuscles": [],
                    "instructions": [],
                    "images": []
                  }
                ]
                """);

        when(repository.findByExternalId("Test_Exercise")).thenReturn(Optional.empty());

        importer.importFromFile(json, null);

        ArgumentCaptor<ExerciceDefinition> captor = ArgumentCaptor.forClass(ExerciceDefinition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getTypeEquipement()).isEqualTo(TypeEquipement.AUTRE);
    }

    @Test
    void importFromFile_unknownLevel_fallsBackToIntermediaire() throws Exception {
        Path json = tempDir.resolve("exercises.json");
        Files.writeString(json, """
                [
                  {
                    "id": "Test_Level",
                    "name": "Test Level",
                    "level": "unknown",
                    "equipment": "barbell",
                    "primaryMuscles": ["chest"],
                    "secondaryMuscles": [],
                    "instructions": [],
                    "images": []
                  }
                ]
                """);

        when(repository.findByExternalId("Test_Level")).thenReturn(Optional.empty());

        importer.importFromFile(json, null);

        ArgumentCaptor<ExerciceDefinition> captor = ArgumentCaptor.forClass(ExerciceDefinition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDifficulte()).isEqualTo(NiveauDifficulte.INTERMEDIAIRE);
    }

    @Test
    void importFromFile_duplicateSecondaryMuscle_notAddedTwice() throws Exception {
        Path json = tempDir.resolve("exercises.json");
        Files.writeString(json, """
                [
                  {
                    "id": "Dup_Muscle",
                    "name": "Dup Muscle",
                    "level": "beginner",
                    "equipment": "body only",
                    "primaryMuscles": ["quadriceps"],
                    "secondaryMuscles": ["quadriceps"],
                    "instructions": [],
                    "images": []
                  }
                ]
                """);

        when(repository.findByExternalId("Dup_Muscle")).thenReturn(Optional.empty());

        importer.importFromFile(json, null);

        ArgumentCaptor<ExerciceDefinition> captor = ArgumentCaptor.forClass(ExerciceDefinition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getMusclesSecondaires()).isEmpty();
    }

    // ── Image copying ──────────────────────────────────────────────────────────

    @Test
    void importFromFile_withImages_copiesFilesToUploadsDir() throws Exception {
        Path sourceDir = tempDir.resolve("source");
        Path exoImgDir = sourceDir.resolve("Barbell_Squat");
        Files.createDirectories(exoImgDir);
        Files.writeString(exoImgDir.resolve("0.jpg"), "image0");
        Files.writeString(exoImgDir.resolve("1.jpg"), "image1");

        Path json = tempDir.resolve("exercises.json");
        Files.writeString(json, """
                [
                  {
                    "id": "Barbell_Squat",
                    "name": "Barbell Squat",
                    "level": "intermediate",
                    "equipment": "barbell",
                    "primaryMuscles": ["quadriceps"],
                    "secondaryMuscles": [],
                    "instructions": [],
                    "images": ["Barbell_Squat/0.jpg", "Barbell_Squat/1.jpg"]
                  }
                ]
                """);

        when(repository.findByExternalId("Barbell_Squat")).thenReturn(Optional.empty());

        importer.importFromFile(json, sourceDir);

        Path destDir = tempDir.resolve("exercices").resolve("Barbell_Squat");
        assertThat(destDir.resolve("0.jpg")).exists();
        assertThat(destDir.resolve("1.jpg")).exists();
    }

    @Test
    void importFromFile_withImagesButNoSourceDir_gifPathIsNull() throws Exception {
        Path json = tempDir.resolve("exercises.json");
        Files.writeString(json, """
                [
                  {
                    "id": "Push_Up",
                    "name": "Push-Up",
                    "level": "beginner",
                    "equipment": "body only",
                    "primaryMuscles": ["chest"],
                    "secondaryMuscles": [],
                    "instructions": [],
                    "images": ["Push_Up/0.jpg"]
                  }
                ]
                """);

        when(repository.findByExternalId("Push_Up")).thenReturn(Optional.empty());

        importer.importFromFile(json, null);

        ArgumentCaptor<ExerciceDefinition> captor = ArgumentCaptor.forClass(ExerciceDefinition.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getGifPath()).isNull();
    }

    @Test
    void importFromFile_multipleExercises_returnsCorrectCount() throws Exception {
        Path json = tempDir.resolve("exercises.json");
        Files.writeString(json, """
                [
                  {
                    "id": "Ex1",
                    "name": "Exercise One",
                    "level": "beginner",
                    "equipment": "body only",
                    "primaryMuscles": ["chest"],
                    "secondaryMuscles": [],
                    "instructions": [],
                    "images": []
                  },
                  {
                    "id": "Ex2",
                    "name": "Exercise Two",
                    "level": "intermediate",
                    "equipment": "barbell",
                    "primaryMuscles": ["quadriceps"],
                    "secondaryMuscles": [],
                    "instructions": [],
                    "images": []
                  }
                ]
                """);

        when(repository.findByExternalId(any())).thenReturn(Optional.empty());

        int count = importer.importFromFile(json, null);

        assertThat(count).isEqualTo(2);
        verify(repository, times(2)).save(any());
    }

    @Test
    void importFromFile_badExerciseSingleField_skipsAndContinues() throws Exception {
        Path json = tempDir.resolve("exercises.json");
        Files.writeString(json, """
                [
                  {
                    "id": "",
                    "name": "No ID exercise",
                    "level": "beginner",
                    "equipment": "body only",
                    "primaryMuscles": [],
                    "secondaryMuscles": [],
                    "instructions": [],
                    "images": []
                  },
                  {
                    "id": "Valid_Ex",
                    "name": "Valid Exercise",
                    "level": "beginner",
                    "equipment": "body only",
                    "primaryMuscles": ["chest"],
                    "secondaryMuscles": [],
                    "instructions": [],
                    "images": []
                  }
                ]
                """);

        when(repository.findByExternalId("Valid_Ex")).thenReturn(Optional.empty());

        int count = importer.importFromFile(json, null);

        assertThat(count).isEqualTo(1);
    }
}
