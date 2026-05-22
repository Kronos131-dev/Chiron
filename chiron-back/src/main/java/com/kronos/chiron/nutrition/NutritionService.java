package com.kronos.chiron.nutrition;

import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Façade autour de la liaison Olympus d'un utilisateur Chiron.
 *
 * <p>La liaison s'appuie sur un token d'intégration PERMANENT fourni par Olympus :
 * une fois le compte lié, il le reste indéfiniment — aucune ré-authentification
 * n'est nécessaire. La liaison n'est rompue que par un unlink explicite, ou si
 * Olympus rejette le token (lien révoqué côté Olympus).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NutritionService {

    private final UtilisateurRepository utilisateurRepository;
    private final OlympusClient olympusClient;
    private final OlympusTokenService tokenService;

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

        user.setOlympusTokenEncrypted(tokenService.encrypt(result.token()));
        // Token d'intégration permanent : aucune date d'expiration.
        user.setOlympusTokenExpiresAt(null);
        user.setOlympusUsername(result.olympusUsername());
        user.setOlympusLinkedAt(LocalDateTime.now());
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
     * Renvoie le token de liaison Olympus (déchiffré) de cet utilisateur Chiron.
     * Le token étant permanent, il n'expire jamais : seule l'absence de liaison
     * lève une {@link NotLinkedException}.
     */
    @Transactional(readOnly = true)
    public String getValidToken(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur Chiron introuvable : " + chironUsername));
        if (user.getOlympusTokenEncrypted() == null) {
            throw new NotLinkedException();
        }
<<<<<<< Updated upstream
        if (user.getOlympusTokenExpiresAt() != null
                && user.getOlympusTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ExpiredException();
        }
        try {
            return tokenService.decrypt(user.getOlympusTokenEncrypted());
        } catch (RuntimeException e) {
            // Token illisible (clé de chiffrement changée depuis le link, ex: CHIRON_SECRET_KEY
            // absente → clé éphémère régénérée au boot). On traite comme une liaison à refaire
            // au lieu de laisser l'exception faire planter l'outil Chiron.
            log.warn("OLYMPUS_TOKEN_UNDECRYPTABLE user={} : {}", chironUsername, e.getMessage());
            throw new ExpiredException();
        }
=======
        return tokenService.decrypt(user.getOlympusTokenEncrypted());
>>>>>>> Stashed changes
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
        // Token permanent : une liaison existante n'est jamais « expirée ».
        return new NutritionLinkStatus(
                true,
                false,
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
