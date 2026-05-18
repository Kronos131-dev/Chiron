package com.kronos.chiron.dto;

import com.kronos.chiron.entity.NiveauExperience;
import com.kronos.chiron.entity.ObjectifPrincipal;
import com.kronos.chiron.entity.Sexe;
import com.kronos.chiron.entity.TypeEquipement;

import java.time.LocalDate;
import java.util.Set;

public record UserProfileSetupDto(
        Boolean isOnboarded,
        LocalDate dateNaissance,
        Sexe sexe,
        Double tailleCm,
        Double poidsCorps,
        NiveauExperience niveauExperience,
        ObjectifPrincipal objectifPrincipal,
        Integer frequenceVisee,
        Set<TypeEquipement> materielDisponible,
        String blessures,
        String preferences
) {}
