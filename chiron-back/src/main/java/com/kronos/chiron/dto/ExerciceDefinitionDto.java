package com.kronos.chiron.dto;

import com.kronos.chiron.entity.MuscleGroup;
import com.kronos.chiron.entity.NiveauDifficulte;
import com.kronos.chiron.entity.TypeEquipement;

import java.util.List;

public record ExerciceDefinitionDto(
        Long id,
        String nomFr,
        String nomEn,
        String imageUrl,   // première image (position de départ)
        String imageUrl2,  // deuxième image (position finale) — nullable
        MuscleGroup musclePrincipal,
        List<MuscleGroup> musclesSecondaires,
        TypeEquipement typeEquipement,
        NiveauDifficulte difficulte,
        String descriptionFr,
        String descriptionEn
) {}
