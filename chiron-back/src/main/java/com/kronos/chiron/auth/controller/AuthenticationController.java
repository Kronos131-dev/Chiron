package com.kronos.chiron.auth.controller;

import com.kronos.chiron.auth.dto.AuthenticationRequest;
import com.kronos.chiron.auth.dto.AuthenticationResponse;
import com.kronos.chiron.auth.dto.RegisterRequest;
import com.kronos.chiron.auth.dto.ForgotPasswordRequest;
import com.kronos.chiron.auth.dto.ResetPasswordRequest;
import com.kronos.chiron.auth.service.AuthenticationService;
import com.kronos.chiron.utilisateur.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService service;
    private final SettingsService settingsService;

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(service.register(request));
    }

    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request) {
        return ResponseEntity.ok(service.authenticate(request));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        settingsService.forgotPassword(request.email());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@RequestBody ResetPasswordRequest request) {
        settingsService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok().build();
    }
}
