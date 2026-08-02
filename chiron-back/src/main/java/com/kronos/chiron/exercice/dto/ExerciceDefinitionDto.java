package com.kronos.chiron.exercice.dto;

import com.kronos.chiron.seance.model.CardioType;
import com.kronos.chiron.exercice.model.MuscleGroup;
import com.kronos.chiron.exercice.model.NiveauDifficulte;
import com.kronos.chiron.exercice.model.TypeEquipement;

import java.util.List;

public record ExerciceDefinitionDto(
        Long id,
        String nomFr,
        String nomEn,
        String imageUrl,
        String imageUrl2,
        MuscleGroup musclePrincipal,
        List<MuscleGroup> musclesSecondaires,
        TypeEquipement typeEquipement,
        NiveauDifficulte difficulte,
        String descriptionFr,
        String descriptionEn,
        CardioType cardioType) {
}
