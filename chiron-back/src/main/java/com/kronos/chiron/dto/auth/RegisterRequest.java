package com.kronos.chiron.dto.auth;

/**
 * Data Transfer Object representing a user registration request.
 * Contains the necessary information to create a new user account.
 *
 * @param username The desired username for the new account.
 * @param email    The email address of the new user.
 * @param password The raw password for the new account, which will be encrypted before storage.
 */
public record RegisterRequest(String username, String email, String password) {}
