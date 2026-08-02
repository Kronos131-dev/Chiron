package com.kronos.chiron.auth.dto;

public record ResetPasswordRequest(String token, String newPassword) {
}
