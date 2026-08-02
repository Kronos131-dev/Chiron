package com.kronos.chiron.seance.mapper;

import com.kronos.chiron.seance.dto.ExerciceDto;
import com.kronos.chiron.utilisateur.dto.ProfileDto;
import com.kronos.chiron.seance.dto.SeanceDto;
import com.kronos.chiron.seance.dto.SerieDto;
import com.kronos.chiron.seance.dto.DegressifDto;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.seance.model.Degressif;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class SeanceMapper {

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
                seance.getNote(),
                seance.getStartTime(),
                seance.getEndTime(),
                seance.getWeekNumber(),
                seance.isModele(),
                utilisateurDto,
                exercicesDto
        );
    }

    public ExerciceDto toExerciceDto(Exercice exercice) {
        if (exercice == null) return null;

        var seriesDto = exercice.getSeries().stream()
                .map(this::toSerieDto)
                .collect(Collectors.toList());

        Long definitionId = exercice.getDefinition() != null ? exercice.getDefinition().getId() : null;
        String cardioType = (exercice.getDefinition() != null && exercice.getDefinition().getCardioType() != null)
                ? exercice.getDefinition().getCardioType().name()
                : null;

        return new ExerciceDto(
                exercice.getId(),
                exercice.getNom(),
                exercice.getCommentaire(),
                definitionId,
                seriesDto,
                exercice.getBlockId(),
                exercice.getBlockType(),
                cardioType,
                exercice.isUnilateral()
        );
    }

    public SerieDto toSerieDto(Serie serie) {
        if (serie == null) return null;

        var degressifsDto = serie.getDegressifs() != null ? serie.getDegressifs().stream()
                .map(this::toDegressifDto)
                .collect(Collectors.toList()) : null;

        return new SerieDto(
                serie.getPoids(),
                serie.getNombreReps(),
                serie.getCommentaire(),
                degressifsDto,
                serie.getDureeMin(),
                serie.getDistanceM(),
                serie.getAllureKmh(),
                serie.getPentePct(),
                serie.getCalories()
        );
    }

    public DegressifDto toDegressifDto(Degressif degressif) {
        if (degressif == null) return null;

        return new DegressifDto(
                degressif.getPoids(),
                degressif.getNombreReps()
        );
    }
}
