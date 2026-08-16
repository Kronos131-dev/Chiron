package com.kronos.chiron.nutrition.service.impl;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

import com.kronos.chiron.nutrition.client.OlympusClient;
import com.kronos.chiron.nutrition.dto.NutritionLinkStatus;
import com.kronos.chiron.nutrition.service.NutritionService;
import com.kronos.chiron.nutrition.service.OlympusTokenService;

import java.time.Clock;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class NutritionServiceImpl implements NutritionService {

    private final UtilisateurRepository utilisateurRepository;
    private final OlympusClient olympusClient;
    private final OlympusTokenService tokenService;

    private final Clock clock;
    @Transactional
    @Override
    public NutritionLinkStatus link(String chironUsername, String olympusPseudo, String olympusPassword) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> notFound("Utilisateur Chiron introuvable : " + chironUsername));

        OlympusClient.AuthenticationResult result = olympusClient.authenticate(olympusPseudo, olympusPassword);
        if (result == null) {
            throw new InvalidCredentialsException("Identifiants Olympus refusés.");
        }

        user.setOlympusTokenEncrypted(tokenService.encrypt(result.token()));
        user.setOlympusTokenExpiresAt(null);
        user.setOlympusUsername(result.olympusUsername());
        user.setOlympusLinkedAt(LocalDateTime.now(clock));
        utilisateurRepository.save(user);
        log.info("OLYMPUS_LINKED user={} olympusUsername={}", chironUsername, result.olympusUsername());

        return buildStatus(user);
    }

    @Transactional(readOnly = true)
    @Override
    public NutritionLinkStatus getStatus(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> notFound("Utilisateur Chiron introuvable : " + chironUsername));
        return buildStatus(user);
    }

    @Transactional
    @Override
    public void unlink(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> notFound("Utilisateur Chiron introuvable : " + chironUsername));
        clearLink(user);
        log.info("OLYMPUS_UNLINKED user={}", chironUsername);
    }

    @Transactional(readOnly = true)
    @Override
    public String getValidToken(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> notFound("Utilisateur Chiron introuvable : " + chironUsername));
        if (user.getOlympusTokenEncrypted() == null) {
            throw new NotLinkedException();
        }
        try {
            return tokenService.decrypt(user.getOlympusTokenEncrypted());
        } catch (RuntimeException e) {
            // WHY: token illisible parce que la clé de chiffrement a changé depuis la liaison
            // (CHIRON_SECRET_KEY absente → clé éphémère régénérée au boot). On traite comme une
            // liaison à refaire, au lieu de laisser l'exception faire planter l'outil Chiron.
            log.warn("OLYMPUS_TOKEN_UNDECRYPTABLE user={} : {}", chironUsername, e.getMessage());
            throw new ExpiredException();
        }
    }

    private void clearLink(Utilisateur user) {
        user.setOlympusTokenEncrypted(null);
        user.setOlympusTokenExpiresAt(null);
        user.setOlympusUsername(null);
        user.setOlympusLinkedAt(null);
        utilisateurRepository.save(user);
    }

    private NutritionLinkStatus buildStatus(Utilisateur user) {
        if (user.getOlympusTokenEncrypted() == null) {
            return NutritionLinkStatus.notLinked();
        }
        return new NutritionLinkStatus(
                true,
                false,
                user.getOlympusUsername(),
                user.getOlympusLinkedAt(),
                user.getOlympusTokenExpiresAt());
    }

}
