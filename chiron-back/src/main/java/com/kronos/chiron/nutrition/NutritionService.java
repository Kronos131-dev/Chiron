package com.kronos.chiron.nutrition;

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
public class NutritionService {

    private final UtilisateurRepository utilisateurRepository;
    private final OlympusClient olympusClient;
    private final OlympusTokenService tokenService;

    private final Clock clock;
    @Transactional
    public NutritionLinkStatus link(String chironUsername, String olympusPseudo, String olympusPassword) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur Chiron introuvable : " + chironUsername));

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
    public NutritionLinkStatus getStatus(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur Chiron introuvable : " + chironUsername));
        return buildStatus(user);
    }

    @Transactional
    public void unlink(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur Chiron introuvable : " + chironUsername));
        clearLink(user);
        log.info("OLYMPUS_UNLINKED user={}", chironUsername);
    }

    @Transactional(readOnly = true)
    public String getValidToken(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur Chiron introuvable : " + chironUsername));
        if (user.getOlympusTokenEncrypted() == null) {
            throw new NotLinkedException();
        }
        try {
            return tokenService.decrypt(user.getOlympusTokenEncrypted());
        } catch (RuntimeException e) {
            log.warn("OLYMPUS_TOKEN_UNDECRYPTABLE user={} : {}", chironUsername, e.getMessage());
            throw new ExpiredException();
        }
    }

    @Transactional
    public void invalidateLink(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur Chiron introuvable : " + chironUsername));
        clearLink(user);
        log.info("OLYMPUS_EXPIRED user={}", chironUsername);
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

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String msg) {
            super(msg);
        }
    }

    public static class NotLinkedException extends RuntimeException {
    }
    public static class ExpiredException extends RuntimeException {
    }
}
