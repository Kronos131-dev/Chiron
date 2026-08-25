package com.kronos.chiron.seance.mapper;

import com.kronos.chiron.core.configuration.CentralMapperConfig;
import com.kronos.chiron.seance.dto.DegressifDto;
import com.kronos.chiron.seance.dto.ExerciceDto;
import com.kronos.chiron.seance.dto.SeanceDto;
import com.kronos.chiron.seance.dto.SerieDto;
import com.kronos.chiron.seance.model.Degressif;
import com.kronos.chiron.seance.model.Exercice;
import com.kronos.chiron.seance.model.Seance;
import com.kronos.chiron.seance.model.Serie;
import com.kronos.chiron.utilisateur.dto.ProfileDto;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = CentralMapperConfig.class)
public interface SeanceMapper {

    SeanceDto toDto(Seance seance);

    @Mapping(target = "exerciceDefinitionId", source = "definition.id")
    @Mapping(target = "cardioType", source = "definition.cardioType")
    @Mapping(target = "wodType", source = "definition.wodType")
    ExerciceDto toExerciceDto(Exercice exercice);

    @Mapping(target = "reps", source = "nombreReps")
    SerieDto toSerieDto(Serie serie);

    @Mapping(target = "reps", source = "nombreReps")
    DegressifDto toDegressifDto(Degressif degressif);

    default ProfileDto toUtilisateurDto(Utilisateur utilisateur) {
        if (utilisateur == null) {
            return null;
        }
        return ProfileDto.builder().username(utilisateur.getUsername()).build();
    }
}
