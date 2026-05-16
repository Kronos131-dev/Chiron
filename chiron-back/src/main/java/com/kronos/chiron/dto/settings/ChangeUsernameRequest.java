package com.kronos.chiron.dto.settings;

public record ChangeUsernameRequest(String currentPassword, String newUsername) {}
