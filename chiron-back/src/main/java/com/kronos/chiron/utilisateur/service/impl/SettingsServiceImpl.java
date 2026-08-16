package com.kronos.chiron.utilisateur.service.impl;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;
import static com.kronos.chiron.core.exceptions.ErrorFactory.badRequest;
import static com.kronos.chiron.core.exceptions.ErrorFactory.conflict;

import com.kronos.chiron.utilisateur.service.SettingsService;

import java.time.Clock;

import com.kronos.chiron.auth.service.EmailService;

import com.kronos.chiron.auth.model.PasswordResetToken;
import com.kronos.chiron.utilisateur.dto.AiProviderDto;
import com.kronos.chiron.utilisateur.model.AiProvider;
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
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final JwtService jwtService;
    private final com.kronos.chiron.coach.agent.ChironAgentRouter chironAgentRouter;

    private final Clock clock;
    @Value("${chiron.frontend-url}")
    private String frontendUrl;

    @Override
    public com.kronos.chiron.utilisateur.dto.TrainingPrefsDto getTrainingPrefs(String username) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
        return new com.kronos.chiron.utilisateur.dto.TrainingPrefsDto(
                user.isPoidsHaltereParImplement(), user.isPoidsMachineParCote());
    }

    @Transactional
    @Override
    public void updateTrainingPrefs(String username, boolean halteresParImplement, boolean machineParCote) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
        user.setPoidsHaltereParImplement(halteresParImplement);
        user.setPoidsMachineParCote(machineParCote);
        utilisateurRepository.save(user);
    }

    @Override
    public AiProviderDto getAiProvider(String username) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
        return new AiProviderDto(
                user.getAiProvider(), chironAgentRouter.geminiAvailable());
    }

    @Transactional
    @Override
    public void updateAiProvider(String username, AiProvider provider) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
        user.setAiProvider(provider != null ? provider : AiProvider.MISTRAL);
        utilisateurRepository.save(user);
    }

    @Transactional
    @Override
    public void changePassword(String username, String currentPassword, String newPassword) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw badRequest("Mot de passe actuel incorrect");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        utilisateurRepository.save(user);
    }

    @Transactional
    @Override
    public void changeEmail(String username, String newEmail) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
        if (utilisateurRepository.findByEmail(newEmail).isPresent()) {
            throw conflict("Cet email est déjà utilisé");
        }
        user.setEmail(newEmail);
        utilisateurRepository.save(user);
    }

    @Transactional
    @Override
    public void changeIdentity(String username, String prenom, String nom) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
        user.setPrenom(prenom == null || prenom.isBlank() ? null : prenom.trim());
        user.setNom(nom == null || nom.isBlank() ? null : nom.trim());
        utilisateurRepository.save(user);
    }

    @Transactional
    @Override
    public String changeUsername(String username, String newUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(username)
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
        utilisateurRepository.findByUsernameIgnoreCase(newUsername)
                .filter(existing -> !existing.getId().equals(user.getId()))
                .ifPresent(__ -> {
                    throw conflict("Ce pseudo est déjà pris");
                });
        user.setUsername(newUsername);
        utilisateurRepository.save(user);
        return jwtService.generateToken(user);
    }

    @Transactional
    @Override
    public void forgotPassword(String email) {
        Utilisateur user = utilisateurRepository.findByEmail(email).orElse(null);
        if (user == null) {
            // WHY: ne pas révéler si l'email existe ou non.
            return;
        }
        tokenRepository.deleteByUtilisateurAndUsedFalse(user);

        String tokenValue = UUID.randomUUID().toString();
        PasswordResetToken token = PasswordResetToken.builder()
                .token(tokenValue)
                .utilisateur(user)
                .expiresAt(LocalDateTime.now(clock).plusHours(24))
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
    @Override
    public void resetPassword(String tokenValue, String newPassword) {
        PasswordResetToken token = tokenRepository.findByToken(tokenValue)
                .orElseThrow(() -> badRequest("Lien de réinitialisation invalide"));
        if (token.getUsed()) {
            throw badRequest("Ce lien a déjà été utilisé");
        }
        if (token.getExpiresAt().isBefore(LocalDateTime.now(clock))) {
            throw badRequest("Ce lien a expiré");
        }
        Utilisateur user = token.getUtilisateur();
        user.setPassword(passwordEncoder.encode(newPassword));
        utilisateurRepository.save(user);
        token.setUsed(true);
        tokenRepository.save(token);
    }
}
