package com.kronos.chiron.controller;

import com.kronos.chiron.dto.UserProfileSetupDto;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;

@RestController
@RequestMapping("/api/profile-setup")
@RequiredArgsConstructor
public class ProfileSetupController {

    private final UtilisateurRepository utilisateurRepository;

    @GetMapping
    public ResponseEntity<UserProfileSetupDto> getSetup(@AuthenticationPrincipal UserDetails userDetails) {
        Utilisateur user = utilisateurRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
        return ResponseEntity.ok(toDto(user));
    }

    @PutMapping
    @Transactional
    public ResponseEntity<UserProfileSetupDto> saveSetup(@AuthenticationPrincipal UserDetails userDetails,
                                                          @RequestBody UserProfileSetupDto dto) {
        Utilisateur user = utilisateurRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        user.setDateNaissance(dto.dateNaissance());
        user.setSexe(dto.sexe());
        user.setTailleCm(dto.tailleCm());
        if (dto.poidsCorps() != null) user.setPoidsCorps(dto.poidsCorps());
        user.setNiveauExperience(dto.niveauExperience());
        user.setObjectifPrincipal(dto.objectifPrincipal());
        user.setFrequenceVisee(dto.frequenceVisee());
        user.setMaterielDisponible(
                dto.materielDisponible() == null || dto.materielDisponible().isEmpty()
                        ? EnumSet.noneOf(com.kronos.chiron.entity.TypeEquipement.class)
                        : EnumSet.copyOf(dto.materielDisponible())
        );
        user.setBlessures(dto.blessures());
        user.setPreferences(dto.preferences());
        user.setIsOnboarded(true);

        utilisateurRepository.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    private UserProfileSetupDto toDto(Utilisateur u) {
        return new UserProfileSetupDto(
                u.getIsOnboarded(),
                u.getDateNaissance(),
                u.getSexe(),
                u.getTailleCm(),
                u.getPoidsCorps(),
                u.getNiveauExperience(),
                u.getObjectifPrincipal(),
                u.getFrequenceVisee(),
                u.getMaterielDisponible(),
                u.getBlessures(),
                u.getPreferences()
        );
    }
}
