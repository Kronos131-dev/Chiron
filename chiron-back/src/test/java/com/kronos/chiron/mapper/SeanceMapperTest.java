package com.kronos.chiron.mapper;

import com.kronos.chiron.dto.ExerciceDto;
import com.kronos.chiron.dto.SeanceDto;
import com.kronos.chiron.dto.SerieDto;
import com.kronos.chiron.entity.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SeanceMapperTest {

    private SeanceMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SeanceMapper();
    }

    private Utilisateur buildUser(String username) {
        return Utilisateur.builder().id(1L).username(username).build();
    }

    private Serie buildSerie(double poids, int reps) {
        Serie s = new Serie();
        s.setPoids(poids);
        s.setNombreReps(reps);
        s.setCommentaire("ok");
        return s;
    }

    private Exercice buildExercice(String nom, Serie... series) {
        Exercice e = new Exercice();
        e.setNom(nom);
        e.setCommentaire("exo note");
        for (Serie s : series) e.addSerie(s);
        return e;
    }

    private Exercice buildExerciceWithDefinition(String nom, Long definitionId) {
        Exercice e = new Exercice();
        e.setNom(nom);
        e.setCommentaire("note");
        if (definitionId != null) {
            com.kronos.chiron.entity.ExerciceDefinition def =
                    com.kronos.chiron.entity.ExerciceDefinition.builder()
                            .id(definitionId)
                            .nomEn("Bench Press")
                            .musclesSecondaires(new java.util.ArrayList<>())
                            .build();
            e.setDefinition(def);
        }
        return e;
    }

    private Seance buildSeance(String titre, Utilisateur user, Exercice... exercices) {
        Seance seance = new Seance();
        seance.setId(1L);
        seance.setTitre(titre);
        seance.setStartTime(LocalDateTime.of(2025, 1, 15, 10, 0));
        seance.setEndTime(LocalDateTime.of(2025, 1, 15, 11, 0));
        seance.setWeekNumber(3);
        seance.setModele(true);
        seance.setUtilisateur(user);
        for (Exercice e : exercices) seance.addExercice(e);
        return seance;
    }

    @Test
    void toDto_null_returnsNull() {
        assertThat(mapper.toDto(null)).isNull();
    }

    @Test
    void toDto_mapsAllFields() {
        Utilisateur user = buildUser("alice");
        Exercice exo = buildExercice("Squat", buildSerie(100, 5));
        Seance seance = buildSeance("Push Day", user, exo);

        SeanceDto dto = mapper.toDto(seance);

        assertThat(dto.id()).isEqualTo(1L);
        assertThat(dto.titre()).isEqualTo("Push Day");
        assertThat(dto.isModele()).isTrue();
        assertThat(dto.weekNumber()).isEqualTo(3);
        assertThat(dto.utilisateur().getUsername()).isEqualTo("alice");
        assertThat(dto.exercices()).hasSize(1);
        assertThat(dto.exercices().get(0).nom()).isEqualTo("Squat");
    }

    @Test
    void toDto_noUser_setsUtilisateurNull() {
        Seance seance = buildSeance("Test", null);
        SeanceDto dto = mapper.toDto(seance);
        assertThat(dto.utilisateur()).isNull();
    }

    @Test
    void toExerciceDto_null_returnsNull() {
        assertThat(mapper.toExerciceDto(null)).isNull();
    }

    @Test
    void toExerciceDto_mapsSeriesCorrectly() {
        Exercice e = buildExercice("Bench", buildSerie(80, 8), buildSerie(90, 5));
        ExerciceDto dto = mapper.toExerciceDto(e);

        assertThat(dto.nom()).isEqualTo("Bench");
        assertThat(dto.series()).hasSize(2);
        assertThat(dto.series().get(0).poids()).isEqualTo(80.0);
        assertThat(dto.series().get(1).reps()).isEqualTo(5);
    }

    @Test
    void toSerieDto_null_returnsNull() {
        assertThat(mapper.toSerieDto(null)).isNull();
    }

    @Test
    void toSerieDto_mapsFields() {
        Serie s = buildSerie(60.5, 12);
        SerieDto dto = mapper.toSerieDto(s);

        assertThat(dto.poids()).isEqualTo(60.5);
        assertThat(dto.reps()).isEqualTo(12);
        assertThat(dto.commentaire()).isEqualTo("ok");
    }

    @Test
    void toSerieDto_withDegressif_mapsDegressifs() {
        Serie s = buildSerie(100, 5);
        Degressif deg = new Degressif();
        deg.setPoids(80.0);
        deg.setNombreReps(8);
        s.addDegressif(deg);

        SerieDto dto = mapper.toSerieDto(s);

        assertThat(dto.degressifs()).hasSize(1);
        assertThat(dto.degressifs().get(0).poids()).isEqualTo(80.0);
        assertThat(dto.degressifs().get(0).reps()).isEqualTo(8);
    }

    @Test
    void toDegressifDto_null_returnsNull() {
        assertThat(mapper.toDegressifDto(null)).isNull();
    }

    @Test
    void toExerciceDto_withDefinition_mapsDefinitionId() {
        Exercice e = buildExerciceWithDefinition("Bench Press", 99L);
        ExerciceDto dto = mapper.toExerciceDto(e);

        assertThat(dto.exerciceDefinitionId()).isEqualTo(99L);
    }

    @Test
    void toExerciceDto_withoutDefinition_definitionIdIsNull() {
        Exercice e = buildExercice("Squat", buildSerie(100, 5));
        ExerciceDto dto = mapper.toExerciceDto(e);

        assertThat(dto.exerciceDefinitionId()).isNull();
    }

    @Test
    void toExerciceDto_mapsExerciceId() {
        Exercice e = buildExercice("Deadlift");
        e.setId(77L);
        ExerciceDto dto = mapper.toExerciceDto(e);

        assertThat(dto.id()).isEqualTo(77L);
    }

    @Test
    void toDto_seanceWithNoExercises_returnEmptyList() {
        Seance seance = buildSeance("Empty", buildUser("bob"));
        SeanceDto dto = mapper.toDto(seance);
        assertThat(dto.exercices()).isEmpty();
    }
}
