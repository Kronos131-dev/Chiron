package com.kronos.chiron.controller;

import com.kronos.chiron.dto.auth.AuthenticationRequest;
import com.kronos.chiron.dto.auth.AuthenticationResponse;
import com.kronos.chiron.dto.auth.RegisterRequest;
import com.kronos.chiron.dto.settings.ForgotPasswordRequest;
import com.kronos.chiron.dto.settings.ResetPasswordRequest;
import com.kronos.chiron.service.AuthenticationService;
import com.kronos.chiron.service.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller responsible for handling public authentication endpoints.
 * Provides APIs for user registration and login, returning JSON Web Tokens (JWT) for session management.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationService service;
    private final SettingsService settingsService;

    /**
     * Endpoint to register a new user in the system.
     *
     * @param request The data transfer object containing new user details.
     * @return A ResponseEntity containing the authentication token.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @RequestBody RegisterRequest request
    ) {
        return ResponseEntity.ok(service.register(request));
    }

    /**
     * Endpoint to authenticate an existing user.
     *
     * @param request The data transfer object containing login credentials.
     * @return A ResponseEntity containing the generated authentication token.
     */
    @PostMapping("/authenticate")
    public ResponseEntity<AuthenticationResponse> authenticate(
            @RequestBody AuthenticationRequest request
    ) {
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
