package com.kronos.chiron.dto.settings;

public record ChangePasswordRequest(String currentPassword, String newPassword) {}
