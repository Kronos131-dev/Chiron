package com.kronos.chiron.dto.auth;

/**
 * Data Transfer Object representing an authentication login request.
 * Contains the credentials required to authenticate a user.
 *
 * @param username The unique username of the user attempting to log in.
 * @param password The raw password provided by the user.
 */
public record AuthenticationRequest(String username, String password) {}
