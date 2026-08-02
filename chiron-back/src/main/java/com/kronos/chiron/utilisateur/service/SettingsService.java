package com.kronos.chiron.utilisateur.service;

import com.kronos.chiron.auth.service.EmailService;

import com.kronos.chiron.auth.model.PasswordResetToken;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.auth.persistence.PasswordResetTokenRepository;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final com.kronos.chiron.coach.agent.ChironAgentRouter chironAgentRouter;

    @Value("${chiron.frontend-url}")
    private String frontendUrl;

    public com.kronos.chiron.dto.settings.TrainingPrefsDto getTrainingPrefs(String username) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        return new com.kronos.chiron.dto.settings.TrainingPrefsDto(
                user.isPoidsHaltereParImplement(), user.isPoidsMachineParCote());
    }

    @Transactional
    public void updateTrainingPrefs(String username, boolean halteresParImplement, boolean machineParCote) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        user.setPoidsHaltereParImplement(halteresParImplement);
        user.setPoidsMachineParCote(machineParCote);
        utilisateurRepository.save(user);
    }

    public com.kronos.chiron.dto.settings.AiProviderDto getAiProvider(String username) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        return new com.kronos.chiron.dto.settings.AiProviderDto(
                user.getAiProvider(), chironAgentRouter.geminiAvailable());
    }

    @Transactional
    public void updateAiProvider(String username, com.kronos.chiron.utilisateur.model.AiProvider provider) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        user.setAiProvider(provider != null ? provider : com.kronos.chiron.utilisateur.model.AiProvider.MISTRAL);
        utilisateurRepository.save(user);
    }

    @Transactional
    public void changePassword(String username, String currentPassword, String newPassword) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new IllegalArgumentException("Mot de passe actuel incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        utilisateurRepository.save(user);
    }

    @Transactional
    public void changeEmail(String username, String newEmail) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        if (utilisateurRepository.findByEmail(newEmail).isPresent()) {
            throw new IllegalArgumentException("Cet email est déjà utilisé");
        }
        user.setEmail(newEmail);
        utilisateurRepository.save(user);
    }

    @Transactional
    public void changeIdentity(String username, String prenom, String nom) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        user.setPrenom(prenom == null || prenom.isBlank() ? null : prenom.trim());
        user.setNom(nom == null || nom.isBlank() ? null : nom.trim());
        utilisateurRepository.save(user);
    }

    @Transactional
    public String changeUsername(String username, String newUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> new NoSuchElementException("Utilisateur introuvable"));
        // Unicité insensible à la casse (exclut l'utilisateur lui-même)
        utilisateurRepository.findByUsernameIgnoreCase(newUsername)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(__ -> { throw new IllegalArgumentException("Ce pseudo est déjà pris"); });
        user.setUsername(newUsername);
        utilisateurRepository.save(user);
        return jwtService.generateToken(user);
    }

    @Transactional
    public void forgotPassword(String email) {
        Utilisateur user = utilisateurRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // Ne pas révéler si l'email existe ou non
            return;
        }
        // Invalider les tokens précédents non utilisés
        tokenRepository.deleteByUtilisateurAndUsedFalse(user);

        String tokenValue = UUID.randomUUID().toString();
        PasswordResetToken token = PasswordResetToken.builder()
                .token(tokenValue)
                .utilisateur(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        tokenRepository.save(token);

        String baseUrl = frontendUrl.replaceAll("/+$", "").replaceAll("/(chat|login|register)/?$", "");
        String resetLink = baseUrl + "/reset-password?token=" + tokenValue;
        try {
            emailService.sendPasswordResetEmail(email, resetLink);
        } catch (Exception ex) {
            log.warn("Échec de l'envoi de l'email de réinitialisation pour {} : {}", email, ex.getMessage());
        }
    }

    @Transactional
    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> new IllegalArgumentException("Lien de réinitialisation invalide"));
        if (token.getUsed()) {
            throw new IllegalArgumentException("Ce lien a déjà été utilisé");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Ce lien a expiré");
        }
        Utilisateur user = token.getUtilisateur();
        user.setPassword(passwordEncoder.encode(newPassword));
        utilisateurRepository.save(user);
        token.setUsed(true);
        tokenRepository.save(token);
    }
}
