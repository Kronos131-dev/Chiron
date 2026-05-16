package com.kronos.chiron.controller;

import com.kronos.chiron.dto.auth.AuthenticationResponse;
import com.kronos.chiron.dto.settings.ChangeEmailRequest;
import com.kronos.chiron.dto.settings.ChangePasswordRequest;
import com.kronos.chiron.dto.settings.ChangeUsernameRequest;
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
        return ResponseEntity.ok(new UserInfoResponse(userDetails.getUsername(), email));
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
}
