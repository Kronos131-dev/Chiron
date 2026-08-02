package com.kronos.chiron.auth.service;

public interface EmailService {

    void sendPasswordResetEmail(String toEmail, String resetLink);
}
