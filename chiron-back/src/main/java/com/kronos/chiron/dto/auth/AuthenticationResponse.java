package com.kronos.chiron.dto.auth;

/**
 * Data Transfer Object representing the response sent after a successful authentication.
 *
 * @param token The JSON Web Token (JWT) issued to the user for subsequent authenticated requests.
 */
public record AuthenticationResponse(String token) {}
