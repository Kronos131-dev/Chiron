package com.kronos.chiron.nutrition;

import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Façade autour de la liaison Olympus d'un utilisateur Chiron.
 * Vague A : gère uniquement le cycle de vie de la liaison (link / status / unlink).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NutritionService {

    private final UtilisateurRepository utilisateurRepository;
    private final OlympusClient olympusClient;
    private final OlympusTokenService tokenService;

    @Value("${olympus.token-ttl-seconds}")
    private long olympusTokenTtlSeconds;

    /**
     * Lie le compte Olympus d'un utilisateur. Renvoie le statut résultant.
     * Si les identifiants sont invalides, lève {@link InvalidCredentialsException}.
     */
    @Transactional
    public NutritionLinkStatus link(String chironUsername, String olympusPseudo, String olympusPassword) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur Chiron introuvable : " + chironUsername));

        OlympusClient.AuthenticationResult result = olympusClient.authenticate(olympusPseudo, olympusPassword);
        if (result == null) {
            throw new InvalidCredentialsException("Identifiants Olympus refusés.");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setOlympusTokenEncrypted(tokenService.encrypt(result.token()));
        user.setOlympusTokenExpiresAt(now.plusSeconds(olympusTokenTtlSeconds));
        user.setOlympusUsername(result.olympusUsername());
        user.setOlympusLinkedAt(now);
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

    /**
     * Renvoie un token Olympus utilisable (déchiffré) pour cet utilisateur Chiron.
     * Lève NotLinkedException si pas lié, ExpiredException si la fenêtre TTL est dépassée.
     */
    @Transactional(readOnly = true)
    public String getValidToken(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur Chiron introuvable : " + chironUsername));
        if (user.getOlympusTokenEncrypted() == null) {
            throw new NotLinkedException();
        }
        if (user.getOlympusTokenExpiresAt() != null
                && user.getOlympusTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ExpiredException();
        }
        return tokenService.decrypt(user.getOlympusTokenEncrypted());
    }

    /**
     * À appeler quand Olympus a renvoyé 401 : on efface la liaison locale.
     */
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
        boolean expired = user.getOlympusTokenExpiresAt() != null
                && user.getOlympusTokenExpiresAt().isBefore(LocalDateTime.now());
        return new NutritionLinkStatus(
                true,
                expired,
                user.getOlympusUsername(),
                user.getOlympusLinkedAt(),
                user.getOlympusTokenExpiresAt()
        );
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String msg) { super(msg); }
    }

    public static class NotLinkedException extends RuntimeException {}
    public static class ExpiredException extends RuntimeException {}
}
