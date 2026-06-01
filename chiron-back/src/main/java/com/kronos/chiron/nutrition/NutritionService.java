package com.kronos.chiron.nutrition;

import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.nutrition.olympusdb.OlympusNutritionDao;
import com.kronos.chiron.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Façade autour de la liaison Olympus d'un utilisateur Chiron.
 *
 * <p>La liaison se fait une seule fois : l'utilisateur saisit son pseudo (email Olympus) et
 * son mot de passe, vérifiés directement contre la base Olympus (hash BCrypt). On stocke
 * alors l'{@code olympus_user_id} ; toutes les lectures nutrition/poids passent ensuite
 * directement en base Olympus par cet identifiant, sans aucune dépendance à l'API HTTP
 * d'Olympus ni à un token. La liaison n'est rompue que par un unlink explicite.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NutritionService {

    private final UtilisateurRepository utilisateurRepository;
    private final OlympusNutritionDao olympusDao;
    private final PasswordEncoder passwordEncoder;

    /**
     * Lie le compte Olympus d'un utilisateur après vérification de ses identifiants contre
     * la base Olympus. Lève {@link InvalidCredentialsException} si l'email est inconnu ou si
     * le mot de passe ne correspond pas.
     */
    @Transactional
    public NutritionLinkStatus link(String chironUsername, String olympusPseudo, String olympusPassword) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur Chiron introuvable : " + chironUsername));

        OlympusNutritionDao.AuthRow auth = olympusDao.findAuthByEmail(olympusPseudo)
                .orElseThrow(() -> new InvalidCredentialsException("Identifiants Olympus refusés."));
        if (auth.passwordHash() == null || !passwordEncoder.matches(olympusPassword, auth.passwordHash())) {
            throw new InvalidCredentialsException("Identifiants Olympus refusés.");
        }

        user.setOlympusUserId(auth.id());
        user.setOlympusUsername(olympusPseudo);
        user.setOlympusLinkedAt(LocalDateTime.now());
        utilisateurRepository.save(user);
        log.info("OLYMPUS_LINKED user={} olympusUserId={}", chironUsername, auth.id());

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
        user.setOlympusUserId(null);
        user.setOlympusUsername(null);
        user.setOlympusLinkedAt(null);
        utilisateurRepository.save(user);
        log.info("OLYMPUS_UNLINKED user={}", chironUsername);
    }

    /**
     * Renvoie l'identifiant Olympus lié de cet utilisateur Chiron, ou {@link Optional#empty()}
     * si aucune liaison n'existe.
     */
    @Transactional(readOnly = true)
    public Optional<Long> getOlympusUserId(String chironUsername) {
        return utilisateurRepository.findByUsername(chironUsername)
                .map(Utilisateur::getOlympusUserId);
    }

    private NutritionLinkStatus buildStatus(Utilisateur user) {
        if (user.getOlympusUserId() == null) {
            return NutritionLinkStatus.notLinked();
        }
        return new NutritionLinkStatus(
                true,
                false,
                user.getOlympusUsername(),
                user.getOlympusLinkedAt(),
                null);
    }

    public static class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException(String msg) { super(msg); }
    }
}
