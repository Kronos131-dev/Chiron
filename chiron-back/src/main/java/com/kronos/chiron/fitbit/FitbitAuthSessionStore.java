package com.kronos.chiron.fitbit;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stocke en mémoire les autorisations OAuth Fitbit en attente. À chaque démarrage
 * de flux on génère un {@code state} aléatoire qui porte, le temps du redirect,
 * l'id de l'utilisateur Chiron et le {@code code_verifier} PKCE.
 *
 * <p>Le callback Fitbit n'est pas authentifié (aucun JWT) : le {@code state} est
 * le seul lien vers l'utilisateur, et sert aussi de protection CSRF.
 *
 * <p>Stockage en mémoire = OK pour un déploiement mono-instance. En cas de scaling
 * multi-instances, déplacer ces entrées dans une petite table (suivi ultérieur).
 */
@Component
public class FitbitAuthSessionStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    /** Autorisation OAuth en attente de callback. */
    public record PendingAuth(Long chironUserId, String codeVerifier, Instant createdAt) {}

    private final ConcurrentHashMap<String, PendingAuth> pending = new ConcurrentHashMap<>();

    /** Enregistre une autorisation en attente et renvoie le {@code state} généré. */
    public String register(Long chironUserId, String codeVerifier) {
        purgeExpired();
        String state = UUID.randomUUID().toString();
        pending.put(state, new PendingAuth(chironUserId, codeVerifier, Instant.now()));
        return state;
    }

    /** Consomme (retire) l'autorisation correspondant au {@code state}. null si absente ou expirée. */
    public PendingAuth consume(String state) {
        if (state == null) return null;
        PendingAuth p = pending.remove(state);
        if (p == null) return null;
        if (p.createdAt().plus(TTL).isBefore(Instant.now())) return null;
        return p;
    }

    private void purgeExpired() {
        Instant cutoff = Instant.now().minus(TTL);
        pending.entrySet().removeIf(e -> e.getValue().createdAt().isBefore(cutoff));
    }
}
