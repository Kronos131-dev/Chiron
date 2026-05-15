package com.kronos.chiron.mapper;

import com.kronos.chiron.dto.ExerciceDto;
import com.kronos.chiron.dto.ProfileDto;
import com.kronos.chiron.dto.SeanceDto;
import com.kronos.chiron.dto.SerieDto;
import com.kronos.chiron.dto.DegressifDto;
import com.kronos.chiron.entity.Exercice;
import com.kronos.chiron.entity.Seance;
import com.kronos.chiron.entity.Serie;
import com.kronos.chiron.entity.Degressif;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

/**
 * Mapper component responsible for converting entity objects related to workout sessions
 * (Seance, Exercice, Serie) into their corresponding Data Transfer Objects (DTOs).
 * Facilitates the separation of persistence layer models from API response structures.
 */
@Component
public class SeanceMapper {

    /**
     * Converts a Seance entity to a SeanceDto.
     * Recursively maps all associated exercises and conditionally maps the user profile.
     *
     * @param seance The Seance entity to convert.
     * @return The corresponding SeanceDto, or null if the input is null.
     */
    public SeanceDto toDto(Seance seance) {
        if (seance == null) return null;

        var exercicesDto = seance.getExercices().stream()
                .map(this::toExerciceDto)
                .collect(Collectors.toList());

        ProfileDto utilisateurDto = null;
        if (seance.getUtilisateur() != null) {
            utilisateurDto = ProfileDto.builder()
                .username(seance.getUtilisateur().getUsername())
                .build();
        }

        return new SeanceDto(
                seance.getId(),
                seance.getTitre(),
                seance.getStartTime(),
                seance.getEndTime(),
                seance.getWeekNumber(),
                seance.isModele(),
                utilisateurDto,
                exercicesDto
        );
    }

    /**
     * Converts an Exercice entity to an ExerciceDto.
     * Recursively maps all associated sets (series).
     *
     * @param exercice The Exercice entity to convert.
     * @return The corresponding ExerciceDto, or null if the input is null.
     */
    public ExerciceDto toExerciceDto(Exercice exercice) {
        if (exercice == null) return null;

        var seriesDto = exercice.getSeries().stream()
                .map(this::toSerieDto)
                .collect(Collectors.toList());

        Long definitionId = exercice.getDefinition() != null ? exercice.getDefinition().getId() : null;

        return new ExerciceDto(
                exercice.getId(),
                exercice.getNom(),
                exercice.getCommentaire(),
                definitionId,
                seriesDto
        );
    }

    /**
     * Converts a Serie entity to a SerieDto.
     *
     * @param serie The Serie entity to convert.
     * @return The corresponding SerieDto, or null if the input is null.
     */
    public SerieDto toSerieDto(Serie serie) {
        if (serie == null) return null;

        var degressifsDto = serie.getDegressifs() != null ? serie.getDegressifs().stream()
                .map(this::toDegressifDto)
                .collect(Collectors.toList()) : null;

        return new SerieDto(
                serie.getPoids(),
                serie.getNombreReps(),
                serie.getCommentaire(),
                degressifsDto
        );
    }

    /**
     * Converts a Degressif entity to a DegressifDto.
     *
     * @param degressif The Degressif entity to convert.
     * @return The corresponding DegressifDto, or null if the input is null.
     */
    public DegressifDto toDegressifDto(Degressif degressif) {
        if (degressif == null) return null;

        return new DegressifDto(
                degressif.getPoids(),
                degressif.getNombreReps()
        );
    }
}
