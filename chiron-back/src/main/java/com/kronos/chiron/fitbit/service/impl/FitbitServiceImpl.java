package com.kronos.chiron.fitbit.service.impl;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;
import com.kronos.chiron.core.exceptions.ChironTechnicalException;

import com.kronos.chiron.fitbit.client.FitbitClient;
import com.kronos.chiron.fitbit.client.FitbitParser;
import com.kronos.chiron.fitbit.dto.FitbitDashboardDto;
import com.kronos.chiron.fitbit.dto.FitbitDayPoint;
import com.kronos.chiron.fitbit.dto.FitbitLinkStatus;
import com.kronos.chiron.fitbit.service.FitbitAuthSessionStore;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.service.SanteSyncService;

import java.time.Clock;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.core.security.TokenCipherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class FitbitServiceImpl implements FitbitService {

    private final UtilisateurRepository utilisateurRepository;
    private final FitbitClient fitbitClient;
    private final TokenCipherService tokenCipher;
    private final FitbitAuthSessionStore authSessionStore;
    private final ObjectProvider<SanteSyncService> santeSyncServiceProvider;

    private final Clock clock;
    @Value("${fitbit.authorize-url}")
    private String authorizeUrl;
    @Value("${fitbit.client-id}")
    private String clientId;
    @Value("${fitbit.redirect-uri}")
    private String redirectUri;
    @Value("${fitbit.scope}")
    private String scope;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long REFRESH_SKEW_SECONDS = 60;

    @Transactional(readOnly = true)
    @Override
    public String buildAuthorizationUrl(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> notFound("Utilisateur introuvable : " + chironUsername));

        String codeVerifier = generateCodeVerifier();
        String codeChallenge = deriveCodeChallenge(codeVerifier);
        String state = authSessionStore.register(user.getId(), codeVerifier);

        // WHY: access_type=offline + prompt=consent sont indispensables pour que Google
        // délivre un refresh token, et le redélivre à chaque reconsentement.
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

    @Transactional
    @Override
    public FitbitLinkStatus handleCallback(String code, String state) {
        FitbitAuthSessionStore.PendingAuth pending = authSessionStore.consume(state);
        if (pending == null) {
            throw new InvalidStateException();
        }
        FitbitClient.TokenResponse tr = fitbitClient.exchangeCode(code, pending.codeVerifier());

        Utilisateur user = utilisateurRepository.findById(pending.chironUserId())
                .orElseThrow(
                        () -> notFound("Utilisateur introuvable id=" + pending.chironUserId()));

        applyTokens(user, tr);
        user.setFitbitLinkedAt(LocalDateTime.now(clock));
        utilisateurRepository.save(user);
        log.info("FITBIT_LINKED user={} fitbitUserId={}", user.getUsername(), tr.fitbitUserId());
        SanteSyncService santeSyncService = santeSyncServiceProvider.getIfAvailable();
        if (santeSyncService != null) {
            santeSyncService.ensureBackfillAsync(user.getUsername());
        }
        return buildStatus(user);
    }

    @Transactional(readOnly = true)
    @Override
    public FitbitLinkStatus getStatus(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> notFound("Utilisateur introuvable : " + chironUsername));
        return buildStatus(user);
    }

    @Transactional
    @Override
    public void unlink(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> notFound("Utilisateur introuvable : " + chironUsername));
        clearLink(user);
        log.info("FITBIT_UNLINKED user={}", chironUsername);
    }

    // WHY: NotLinkedException et ExpiredException sont des reponses, pas des pannes. Sans cette
    // clause, la transaction partagee etait marquee rollback-only avant que l'appelant ne les
    // attrape : son propre travail partait avec, et le commit rendait UnexpectedRollbackException.
    // C'est ce qui vidait le rattrapage des calories d'un compte non lie et faisait repondre 500
    // au tableau de bord sante du meme athlete — l'exception qu'on attrape ne dit rien de plus
    // qu'un compte a relier, et le clearLink ci-dessous doit justement survivre.
    @Transactional(noRollbackFor = {NotLinkedException.class, ExpiredException.class})
    @Override
    public String getValidToken(String chironUsername) {
        Utilisateur user = utilisateurRepository.findByUsername(chironUsername)
                .orElseThrow(() -> notFound("Utilisateur introuvable : " + chironUsername));

        if (user.getFitbitRefreshTokenEncrypted() == null && user.getFitbitAccessTokenEncrypted() == null) {
            throw new NotLinkedException();
        }

        if (user.getFitbitAccessTokenEncrypted() != null
                && user.getFitbitTokenExpiresAt() != null
                && user.getFitbitTokenExpiresAt().isAfter(LocalDateTime.now(clock).plusSeconds(REFRESH_SKEW_SECONDS))) {
            try {
                return tokenCipher.decrypt(user.getFitbitAccessTokenEncrypted());
            } catch (RuntimeException e) {
                log.warn("FITBIT_TOKEN_UNDECRYPTABLE user={} : {}", chironUsername, e.getMessage());
                throw new ExpiredException();
            }
        }

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

    @Transactional
    @Override
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

        LocalDate today = LocalDate.now(clock);
        LocalDate start = today.minusDays(n - 1L);
        try {
            Map<LocalDate, Integer> stepsByDate = FitbitParser
                    .stepsByDate(fitbitClient.rollUpDailySteps(token, start, today));
            Map<LocalDate, Double> sleepByDate = FitbitParser.sleepHoursByDate(fitbitClient.listSleep(token, start));
            Map<LocalDate, Integer> hrByDate = FitbitParser
                    .restingHeartRateByDate(fitbitClient.listRestingHeartRate(token, start));

            List<FitbitDayPoint> dayPoints = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                LocalDate d = start.plusDays(i);
                dayPoints.add(new FitbitDayPoint(d, stepsByDate.get(d), sleepByDate.get(d)));
            }
            return new FitbitDashboardDto(true, false, true,
                    stepsByDate.get(today),
                    null,
                    null,
                    null,
                    sleepByDate.get(today),
                    mostRecent(hrByDate),
                    dayPoints);
        } catch (FitbitClient.FitbitUnauthorizedException e) {
            // WHY: l'API data a rejeté le token, mais le refresh token reste valable : on NE
            // délie PAS le compte ici. Seul un refresh rejeté (getValidToken) prouve que le
            // grant OAuth est révoqué et justifie une reconnexion.
            log.warn("FITBIT_DASHBOARD_UNAUTHORIZED user={} : {}", chironUsername, e.getMessage());
            return FitbitDashboardDto.unavailable();
        } catch (FitbitClient.FitbitUnavailableException e) {
            log.warn("FITBIT_DASHBOARD_UNAVAILABLE user={} : {}", chironUsername, e.getMessage());
            return FitbitDashboardDto.unavailable();
        }
    }

    private static Integer mostRecent(Map<LocalDate, Integer> byDate) {
        return byDate.entrySet().stream()
                .max(Map.Entry.comparingByKey())
                .map(Map.Entry::getValue)
                .orElse(null);
    }

    private void applyTokens(Utilisateur user, FitbitClient.TokenResponse tr) {
        user.setFitbitAccessTokenEncrypted(tokenCipher.encrypt(tr.accessToken()));
        if (tr.refreshToken() != null) {
            user.setFitbitRefreshTokenEncrypted(tokenCipher.encrypt(tr.refreshToken()));
        }
        user.setFitbitTokenExpiresAt(LocalDateTime.now(clock).plusSeconds(tr.expiresInSeconds()));
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
        boolean needsReconnect = user.getFitbitRefreshTokenEncrypted() == null
                && (user.getFitbitTokenExpiresAt() == null
                        || user.getFitbitTokenExpiresAt().isBefore(LocalDateTime.now(clock)));
        return new FitbitLinkStatus(true, needsReconnect, user.getFitbitUserId(),
                user.getFitbitScope(), user.getFitbitLinkedAt());
    }

    private String generateCodeVerifier() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String deriveCodeChallenge(String verifier) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            throw new ChironTechnicalException("SHA-256 indisponible", e);
        }
    }

}
