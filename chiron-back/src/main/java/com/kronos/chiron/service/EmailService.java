package com.kronos.chiron.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Chiron — Réinitialisation de votre mot de passe");
        message.setText(
                "Bonjour,\n\n" +
                "Vous avez demandé la réinitialisation de votre mot de passe Chiron.\n\n" +
                "Cliquez sur le lien ci-dessous (valable 24h) :\n" +
                resetLink + "\n\n" +
                "Si vous n'êtes pas à l'origine de cette demande, ignorez ce message.\n\n" +
                "— Chiron"
        );
        mailSender.send(message);
    }
}
