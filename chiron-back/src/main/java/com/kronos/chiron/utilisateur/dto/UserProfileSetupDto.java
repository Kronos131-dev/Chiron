package com.kronos.chiron.utilisateur.dto;

import com.kronos.chiron.utilisateur.model.NiveauExperience;
import com.kronos.chiron.utilisateur.model.ObjectifPrincipal;
import com.kronos.chiron.utilisateur.model.Sexe;
import com.kronos.chiron.exercice.model.TypeEquipement;

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
        String preferences) {
}
