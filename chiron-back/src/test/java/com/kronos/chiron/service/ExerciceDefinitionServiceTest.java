package com.kronos.chiron.service;

import com.kronos.chiron.dto.ExerciceDefinitionDto;
import com.kronos.chiron.entity.*;
import com.kronos.chiron.repository.ExerciceDefinitionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExerciceDefinitionServiceTest {

    @Mock
    private ExerciceDefinitionRepository repository;

    @InjectMocks
    private ExerciceDefinitionService service;

    private ExerciceDefinition buildDef(Long id, String nomFr, String nomEn, MuscleGroup muscle) {
        return ExerciceDefinition.builder()
                .id(id)
                .nomFr(nomFr)
                .nomEn(nomEn)
                .musclePrincipal(muscle)
                .typeEquipement(TypeEquipement.BARRE)
                .difficulte(NiveauDifficulte.INTERMEDIAIRE)
                .musclesSecondaires(List.of())
                .gifPath("Barbell_Squat")
                .build();
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    void search_delegatesToRepository_andMapsToDto() {
        ExerciceDefinition def = buildDef(1L, "Squat barre", "Barbell Squat", MuscleGroup.QUADRICEPS);
        when(repository.search(null, "%", null, null, null)).thenReturn(List.of(def));

        List<ExerciceDefinitionDto> results = service.search(null, null, null, null);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).nomFr()).isEqualTo("Squat barre");
        assertThat(results.get(0).nomEn()).isEqualTo("Barbell Squat");
        assertThat(results.get(0).musclePrincipal()).isEqualTo(MuscleGroup.QUADRICEPS);
    }

    @Test
    void search_invalidMuscleName_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.search(null, "INVALID_MUSCLE", null, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void search_parsesEnumFilters() {
        when(repository.search("%squat%", "squat%", MuscleGroup.QUADRICEPS, TypeEquipement.BARRE, NiveauDifficulte.INTERMEDIAIRE))
                .thenReturn(List.of());

        List<ExerciceDefinitionDto> results = service.search("squat", "QUADRICEPS", "BARRE", "INTERMEDIAIRE");

        assertThat(results).isEmpty();
        verify(repository).search("%squat%", "squat%", MuscleGroup.QUADRICEPS, TypeEquipement.BARRE, NiveauDifficulte.INTERMEDIAIRE);
    }

    @Test
    void search_limitsTo50Results() {
        List<ExerciceDefinition> many = java.util.stream.IntStream.range(0, 60)
                .mapToObj(i -> buildDef((long) i, "Exo " + i, "Exercise " + i, MuscleGroup.DOS))
                .toList();
        when(repository.search(null, "%", null, null, null)).thenReturn(many);

        List<ExerciceDefinitionDto> results = service.search(null, null, null, null);

        assertThat(results).hasSize(50);
    }

    @Test
    void search_blankQuery_passedAsNullPattern() {
        when(repository.search(null, "%", null, null, null)).thenReturn(List.of());

        service.search("  ", null, null, null);

        verify(repository).search(null, "%", null, null, null);
    }

    @Test
    void search_nonBlankQuery_passedAsLowercasePattern() {
        when(repository.search("%squat%", "squat%", null, null, null)).thenReturn(List.of());

        service.search("Squat", null, null, null);

        verify(repository).search("%squat%", "squat%", null, null, null);
    }

    // ── getById ───────────────────────────────────────────────────────────────

    @Test
    void getById_found_returnsDto() {
        ExerciceDefinition def = buildDef(42L, "Développé couché", "Bench Press", MuscleGroup.PECTORAUX);
        when(repository.findById(42L)).thenReturn(Optional.of(def));

        ExerciceDefinitionDto dto = service.getById(42L);

        assertThat(dto.id()).isEqualTo(42L);
        assertThat(dto.nomFr()).isEqualTo("Développé couché");
    }

    @Test
    void getById_notFound_throwsNoSuchElement() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── toDto / imageUrl building ─────────────────────────────────────────────

    @Test
    void toDto_withGifPath_buildsImageUrls() {
        ExerciceDefinition def = buildDef(5L, null, "Pull-Up", MuscleGroup.DOS);
        when(repository.search(null, "%", null, null, null)).thenReturn(List.of(def));

        ExerciceDefinitionDto dto = service.search(null, null, null, null).get(0);

        assertThat(dto.imageUrl()).isEqualTo("/api/exercices/5/image/0");
        assertThat(dto.imageUrl2()).isEqualTo("/api/exercices/5/image/1");
    }

    @Test
    void toDto_withoutGifPath_imageUrlsAreNull() {
        ExerciceDefinition def = ExerciceDefinition.builder()
                .id(7L)
                .nomEn("Unknown")
                .gifPath(null)
                .musclesSecondaires(List.of())
                .build();
        when(repository.search(null, "%", null, null, null)).thenReturn(List.of(def));

        ExerciceDefinitionDto dto = service.search(null, null, null, null).get(0);

        assertThat(dto.imageUrl()).isNull();
        assertThat(dto.imageUrl2()).isNull();
    }

    // ── streamImage ───────────────────────────────────────────────────────────

    @Test
    void streamImage_invalidIndex_throwsIllegalArgument() {
        assertThatThrownBy(() -> service.streamImage(10L, 2))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void streamImage_exerciceNotFound_throwsNoSuchElement() {
        when(repository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.streamImage(99L, 0))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void streamImage_index0_imageDataNull_throwsNoSuchElement() {
        when(repository.existsById(12L)).thenReturn(true);
        when(repository.findImage0ById(12L)).thenReturn(null);

        assertThatThrownBy(() -> service.streamImage(12L, 0))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void streamImage_index1_imageDataNull_throwsNoSuchElement() {
        when(repository.existsById(12L)).thenReturn(true);
        when(repository.findImage1ById(12L)).thenReturn(null);

        assertThatThrownBy(() -> service.streamImage(12L, 1))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void streamImage_index0_returnsResource() {
        byte[] fakeData = "fake-image".getBytes();
        when(repository.existsById(10L)).thenReturn(true);
        when(repository.findImage0ById(10L)).thenReturn(fakeData);

        Resource res = service.streamImage(10L, 0);

        assertThat(res).isNotNull();
        assertThat(res.isReadable()).isTrue();
    }

    @Test
    void streamImage_index1_returnsResource() {
        byte[] fakeData = "fake-image".getBytes();
        when(repository.existsById(11L)).thenReturn(true);
        when(repository.findImage1ById(11L)).thenReturn(fakeData);

        Resource res = service.streamImage(11L, 1);

        assertThat(res).isNotNull();
        assertThat(res.isReadable()).isTrue();
    }
}
