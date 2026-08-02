package com.kronos.chiron.fitbit;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.core.security.TokenCipherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * Façade de la liaison Fitbit (OAuth2 Authorization Code + PKCE). Calque
 * {@code NutritionService}. Pièce nouvelle vs Olympus : {@link #getValidToken}
 * rafraîchit automatiquement l'access token via le refresh token stocké.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FitbitService {

    private final UtilisateurRepository utilisateurRepository;
    private final FitbitClient fitbitClient;
    private final TokenCipherService tokenCipher;
    private final FitbitAuthSessionStore authSessionStore;

    @Value("${fitbit.authorize-url}")
    private String authorizeUrl;
    @Value("${fitbit.client-id}")
    private String clientId;
    @Value("${fitbit.redirect-uri}")
    private String redirectUri;
    @Value("${fitbit.scope}")
    private String scope;

    private static final SecureRandom RANDOM = new SecureRandom();
    /** Marge de sécurité : on rafraîchit dès qu'il reste moins de 60 s de validité. */
    private static final long REFRESH_SKEW_SECONDS = 60;

    /** Construit l'URL de consentement Fitbit pour un utilisateur (génère PKCE + state). */
    @Transactional(readOnly = true)
    public String buildAuthorizationUrl(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + chironUsername));

        String codeVerifier = generateCodeVerifier();
        String codeChallenge = deriveCodeChallenge(codeVerifier);
        String state = authSessionStore.register(user.getId(), codeVerifier);

        // access_type=offline + prompt=consent : indispensables pour que Google
        // délivre un refresh token (et le redélivre à chaque reconsentement).
        return UriComponentsBuilder.fromUriString(authorizeUrl)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("scope", scope)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("code_challenge", codeChallenge)
                .queryParam("code_challenge_method", "S256")
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .build()
                .encode()
                .toUriString();
    }

    /** Traite le retour OAuth : échange le code contre des tokens et les persiste. */
    @Transactional
    public FitbitLinkStatus handleCallback(String code, String state) {
        FitbitAuthSessionStore.PendingAuth pending = authSessionStore.consume(state);
        if (pending == null) {
            throw new InvalidStateException();
        }
        FitbitClient.TokenResponse tr = fitbitClient.exchangeCode(code, pending.codeVerifier());

        Utilisateur user = utilisateurRepository.findById(pending.chironUserId())
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable id=" + pending.chironUserId()));

        applyTokens(user, tr);
        user.setFitbitLinkedAt(LocalDateTime.now());
        utilisateurRepository.save(user);
        log.info("FITBIT_LINKED user={} fitbitUserId={}", user.getUsername(), tr.fitbitUserId());
        return buildStatus(user);
    }

    @Transactional(readOnly = true)
    public FitbitLinkStatus getStatus(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + chironUsername));
        return buildStatus(user);
    }

    @Transactional
    public void unlink(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + chironUsername));
        clearLink(user);
        log.info("FITBIT_UNLINKED user={}", chironUsername);
    }

    /**
     * Renvoie un access token Fitbit valide, en le rafraîchissant si nécessaire.
     *
     * @throws NotLinkedException si l'utilisateur n'a pas lié Fitbit
     * @throws ExpiredException   si le token ne peut être rafraîchi (refresh révoqué / illisible)
     */
    @Transactional
    public String getValidToken(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable : " + chironUsername));

        if (user.getFitbitRefreshTokenEncrypted() == null && user.getFitbitAccessTokenEncrypted() == null) {
            throw new NotLinkedException();
        }

        // Access token encore valide (avec marge) → on le renvoie directement.
        if (user.getFitbitAccessTokenEncrypted() != null
                && user.getFitbitTokenExpiresAt() != null
                && user.getFitbitTokenExpiresAt().isAfter(LocalDateTime.now().plusSeconds(REFRESH_SKEW_SECONDS))) {
            try {
                return tokenCipher.decrypt(user.getFitbitAccessTokenEncrypted());
            } catch (RuntimeException e) {
                log.warn("FITBIT_TOKEN_UNDECRYPTABLE user={} : {}", chironUsername, e.getMessage());
                throw new ExpiredException();
            }
        }

        // Sinon : rafraîchir via le refresh token.
        if (user.getFitbitRefreshTokenEncrypted() == null) {
            throw new ExpiredException();
        }
        String refreshToken;
        try {
            refreshToken = tokenCipher.decrypt(user.getFitbitRefreshTokenEncrypted());
        } catch (RuntimeException e) {
            log.warn("FITBIT_REFRESH_UNDECRYPTABLE user={} : {}", chironUsername, e.getMessage());
            throw new ExpiredException();
        }
        try {
            FitbitClient.TokenResponse tr = fitbitClient.refresh(refreshToken);
            applyTokens(user, tr);
            utilisateurRepository.save(user);
            log.info("FITBIT_TOKEN_REFRESHED user={}", chironUsername);
            return tr.accessToken();
        } catch (FitbitClient.FitbitUnauthorizedException e) {
            clearLink(user);
            log.info("FITBIT_EXPIRED user={} (refresh rejeté)", chironUsername);
            throw new ExpiredException();
        }
    }

    /**
     * Construit le dashboard Fitbit (activité du jour, sommeil, FC de repos, séries
     * journalières) sur {@code days} jours. Best-effort vis-à-vis de l'état de liaison :
     * renvoie un DTO {@code linked=false} / {@code needsReconnect=true} / {@code unavailable}
     * plutôt que de lever une exception, pour un front simple.
     */
    @Transactional
    public FitbitDashboardDto getDashboard(String chironUsername, int days) {
        int n = Math.max(1, Math.min(days, 30));
        String token;
        try {
            token = getValidToken(chironUsername);
        } catch (NotLinkedException e) {
            return FitbitDashboardDto.notLinked();
        } catch (ExpiredException e) {
            return FitbitDashboardDto.reconnectNeeded();
        }

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(n - 1L);
        try {
            Map<LocalDate, Integer> stepsByDate =
                    FitbitParser.stepsByDate(fitbitClient.rollUpDailySteps(token, start, today));
            Map<LocalDate, Double> sleepByDate =
                    FitbitParser.sleepHoursByDate(fitbitClient.listSleep(token, start));
            Map<LocalDate, Integer> hrByDate =
                    FitbitParser.restingHeartRateByDate(fitbitClient.listRestingHeartRate(token, start));

            List<FitbitDayPoint> dayPoints = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                LocalDate d = start.plusDays(i);
                dayPoints.add(new FitbitDayPoint(d, stepsByDate.get(d), sleepByDate.get(d)));
            }
            return new FitbitDashboardDto(true, false, true,
                    stepsByDate.get(today),
                    null,   // minutes actives — non exposées par cette 1re version Google Health
                    null,   // distance — idem
                    null,   // calories — idem
                    sleepByDate.get(today),
                    mostRecent(hrByDate),
                    dayPoints);
        } catch (FitbitClient.FitbitUnauthorizedException e) {
            // L'API data a rejeté le token, mais le refresh token reste valable :
            // on NE délie PAS le compte ici. Seul un refresh rejeté (getValidToken)
            // prouve que le grant OAuth est révoqué et justifie une reconnexion.
            log.warn("FITBIT_DASHBOARD_UNAUTHORIZED user={} : {}", chironUsername, e.getMessage());
            return FitbitDashboardDto.unavailable();
        } catch (FitbitClient.FitbitUnavailableException e) {
            log.warn("FITBIT_DASHBOARD_UNAVAILABLE user={} : {}", chironUsername, e.getMessage());
            return FitbitDashboardDto.unavailable();
        }
    }

    /** Valeur la plus récente (date la plus grande) d'une map indexée par jour. */
    private static Integer mostRecent(Map<LocalDate, Integer> byDate) {
        return byDate.entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    /** Applique une réponse de token sur l'utilisateur (chiffre access + refresh, rotation incluse). */
    private void applyTokens(Utilisateur user, FitbitClient.TokenResponse tr) {
        user.setFitbitAccessTokenEncrypted(tokenCipher.encrypt(tr.accessToken()));
        if (tr.refreshToken() != null) {
            user.setFitbitRefreshTokenEncrypted(tokenCipher.encrypt(tr.refreshToken()));
        }
        user.setFitbitTokenExpiresAt(LocalDateTime.now().plusSeconds(tr.expiresInSeconds()));
        if (tr.scope() != null) user.setFitbitScope(tr.scope());
        if (tr.fitbitUserId() != null) user.setFitbitUserId(tr.fitbitUserId());
    }

    private void clearLink(Utilisateur user) {
        user.setFitbitAccessTokenEncrypted(null);
        user.setFitbitRefreshTokenEncrypted(null);
        user.setFitbitTokenExpiresAt(null);
        user.setFitbitUserId(null);
        user.setFitbitScope(null);
        user.setFitbitLinkedAt(null);
        utilisateurRepository.save(user);
    }

    private FitbitLinkStatus buildStatus(Utilisateur user) {
        boolean linked = user.getFitbitRefreshTokenEncrypted() != null
                || user.getFitbitAccessTokenEncrypted() != null;
        if (!linked) {
            return FitbitLinkStatus.notLinked();
        }
        // needsReconnect : lié, mais plus de refresh token et access token expiré.
        boolean needsReconnect = user.getFitbitRefreshTokenEncrypted() == null
                && (user.getFitbitTokenExpiresAt() == null
                    || user.getFitbitTokenExpiresAt().isBefore(LocalDateTime.now()));
        return new FitbitLinkStatus(true, needsReconnect, user.getFitbitUserId(),
                user.getFitbitScope(), user.getFitbitLinkedAt());
    }

    /** code_verifier PKCE : 48 octets aléatoires → 64 caractères base64url (43-128 requis). */
    private String generateCodeVerifier() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** code_challenge = base64url(SHA-256(code_verifier)), sans padding. */
    private String deriveCodeChallenge(String verifier) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    public static class NotLinkedException extends RuntimeException {}
    public static class ExpiredException extends RuntimeException {}
    public static class InvalidStateException extends RuntimeException {}
}
