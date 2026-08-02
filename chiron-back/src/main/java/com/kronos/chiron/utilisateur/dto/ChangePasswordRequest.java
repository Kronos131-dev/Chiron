package com.kronos.chiron.utilisateur.dto;

public record ChangePasswordRequest(String currentPassword, String newPassword) {
}
