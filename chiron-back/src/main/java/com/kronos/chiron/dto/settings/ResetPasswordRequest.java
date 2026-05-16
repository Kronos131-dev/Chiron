package com.kronos.chiron.dto.settings;

public record ResetPasswordRequest(String token, String newPassword) {}
