package com.kronos.chiron.seance.service.impl;

import com.kronos.chiron.seance.model.Degressif;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.seance.persistence.SeanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeanceResumeServiceImplTest {

    @Mock
    private SeanceRepository seanceRepository;

    @InjectMocks
    private SeanceResumeServiceImpl seanceResumeService;

    @Test
    void decrireContenu_seanceAvecExercices_listeLesSeriesEtLeTotal() {
        // Given
        Exercice developpe = exercice("Développé couché", false, serie(80.0, 10), serie(85.0, 8));
        Exercice dips = exercice("Dips", true, serie(0.0, 12));
        Seance seance = Seance.builder().id(12L).titre("Push A").exercices(List.of(developpe, dips)).build();
        when(seanceRepository.findById(12L)).thenReturn(Optional.of(seance));

        // When
        String result = seanceResumeService.decrireContenu(12L);

        // Then
        assertThat(result)
                .contains("Contenu de la séance 'Push A'")
                .contains("Développé couché : 10 reps @ 80,0kg | 8 reps @ 85,0kg")
                .contains("Dips (unilatéral) : 12 reps @ 0,0kg")
                .contains("3 série(s) au total");
    }

    @Test
    void decrireContenu_serieAvecDegressifs_lesCompteSansLesDetailler() {
        // Given
        Serie serie = serie(100.0, 5);
        serie.setDegressifs(List.of(Degressif.builder().poids(80.0).nombreReps(5).build()));
        Seance seance = Seance.builder().id(12L).titre("Squat").exercices(List.of(exercice("Squat", false, serie)))
                .build();
        when(seanceRepository.findById(12L)).thenReturn(Optional.of(seance));

        // When
        String result = seanceResumeService.decrireContenu(12L);

        // Then
        assertThat(result).contains("5 reps @ 100,0kg + 1 dégressif(s)");
    }

    @Test
    void decrireContenu_seanceIntrouvable_renvoieUneChaineVide() {
        // Given
        when(seanceRepository.findById(99L)).thenReturn(Optional.empty());

        // When
        String result = seanceResumeService.decrireContenu(99L);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void decrireContenu_seanceSansExercice_renvoieUneChaineVide() {
        // Given
        Seance seance = Seance.builder().id(12L).titre("Vide").exercices(List.of()).build();
        when(seanceRepository.findById(12L)).thenReturn(Optional.of(seance));

        // When
        String result = seanceResumeService.decrireContenu(12L);

        // Then
        assertThat(result).isEmpty();
    }

    @Test
    void decrireContenu_identifiantNull_renvoieUneChaineVide() {
        // When
        String result = seanceResumeService.decrireContenu(null);

        // Then
        assertThat(result).isEmpty();
    }

    private Exercice exercice(String nom, boolean unilateral, Serie... series) {
        return Exercice.builder().nom(nom).unilateral(unilateral).series(List.of(series)).build();
    }

    private Serie serie(double poids, int nombreReps) {
        return Serie.builder().poids(poids).nombreReps(nombreReps).build();
    }
}
