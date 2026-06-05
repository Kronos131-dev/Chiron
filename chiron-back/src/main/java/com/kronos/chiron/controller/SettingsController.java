package com.kronos.chiron.controller;

import com.kronos.chiron.dto.auth.AuthenticationResponse;
import com.kronos.chiron.dto.settings.ChangeEmailRequest;
import com.kronos.chiron.dto.settings.ChangeIdentityRequest;
import com.kronos.chiron.dto.settings.ChangePasswordRequest;
import com.kronos.chiron.dto.settings.ChangeUsernameRequest;
import com.kronos.chiron.dto.settings.TrainingPrefsDto;
import com.kronos.chiron.dto.settings.UserInfoResponse;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final SettingsService settingsService;

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> getMe(@AuthenticationPrincipal UserDetails userDetails) {
        String email = (userDetails instanceof Utilisateur u) ? u.getEmail() : null;
        String prenom = (userDetails instanceof Utilisateur u) ? u.getPrenom() : null;
        String nom = (userDetails instanceof Utilisateur u) ? u.getNom() : null;
        return ResponseEntity.ok(new UserInfoResponse(userDetails.getUsername(), email, prenom, nom));
    }

    @PutMapping("/identity")
    public ResponseEntity<Void> changeIdentity(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ChangeIdentityRequest request
    ) {
        settingsService.changeIdentity(userDetails.getUsername(), request.prenom(), request.nom());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ChangePasswordRequest request
    ) {
        settingsService.changePassword(userDetails.getUsername(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/email")
    public ResponseEntity<Void> changeEmail(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ChangeEmailRequest request
    ) {
        settingsService.changeEmail(userDetails.getUsername(), request.newEmail());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/username")
    public ResponseEntity<AuthenticationResponse> changeUsername(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody ChangeUsernameRequest request
    ) {
        String newToken = settingsService.changeUsername(userDetails.getUsername(), request.newUsername());
        return ResponseEntity.ok(new AuthenticationResponse(newToken));
    }

    @GetMapping("/ai-provider")
    public ResponseEntity<com.kronos.chiron.dto.settings.AiProviderDto> getAiProvider(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(settingsService.getAiProvider(userDetails.getUsername()));
    }

    @PutMapping("/ai-provider")
    public ResponseEntity<Void> updateAiProvider(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody com.kronos.chiron.dto.settings.AiProviderDto request) {
        settingsService.updateAiProvider(userDetails.getUsername(), request.provider());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/training-prefs")
    public ResponseEntity<TrainingPrefsDto> getTrainingPrefs(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(settingsService.getTrainingPrefs(userDetails.getUsername()));
    }

    @PutMapping("/training-prefs")
    public ResponseEntity<Void> updateTrainingPrefs(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody TrainingPrefsDto request
    ) {
        settingsService.updateTrainingPrefs(
                userDetails.getUsername(), request.halteresParImplement(), request.machineParCote());
        return ResponseEntity.ok().build();
    }
}
