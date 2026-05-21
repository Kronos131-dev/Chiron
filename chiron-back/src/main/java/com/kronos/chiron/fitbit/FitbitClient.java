package com.kronos.chiron.fitbit;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.Map;

/**
 * Client HTTP des données santé Fitbit, via la <b>Google Health API</b>
 * ({@code health.googleapis.com}) — l'API Fitbit Web legacy étant arrêtée en
 * septembre 2026. L'OAuth est délégué à Google ({@code oauth2.googleapis.com}).
 *
 * <p>NB : certains noms de champs/filtres ci-dessous sont issus de la doc
 * Google Health et n'ont pas pu être validés contre un compte réel — ils sont
 * signalés par « VÉRIFIER » et le parsing les traite de façon défensive.
 */
@Component
@Slf4j
public class FitbitClient {

    private final RestClient restClient;
    private final String tokenUrl;
    private final String redirectUri;
    private final String clientId;
    private final String clientSecret;

    public FitbitClient(
            @Value("${fitbit.api-base-url}") String apiBaseUrl,
            @Value("${fitbit.token-url}") String tokenUrl,
            @Value("${fitbit.redirect-uri}") String redirectUri,
            @Value("${fitbit.client-id}") String clientId,
            @Value("${fitbit.client-secret}") String clientSecret) {
        this.tokenUrl = tokenUrl;
        this.redirectUri = redirectUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = RestClient.builder().baseUrl(apiBaseUrl).build();
        log.info("FitbitClient configuré (Google Health API, apiBaseUrl={})", apiBaseUrl);
    }

    // ─────────────────────────── OAuth (Google) ───────────────────────────

    /** Échange un code d'autorisation Google contre des tokens (PKCE inclus). */
    public TokenResponse exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);
        return postToken(form);
    }

    /**
     * Rafraîchit l'access token. Contrairement à Fitbit legacy, Google NE fait
     * PAS tourner le refresh token : la réponse ne contient en général pas de
     * nouveau {@code refresh_token}, et l'ancien reste valable.
     */
    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return postToken(form);
    }

    /** Form de base : Google attend les identifiants client dans le corps. */
    private MultiValueMap<String, String> baseForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        return form;
    }

    private TokenResponse postToken(MultiValueMap<String, String> form) {
        try {
            JsonNode body = restClient.post()
                    .uri(tokenUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null || !body.hasNonNull("access_token")) {
                throw new FitbitUnavailableException("Réponse OAuth Google invalide : access_token absent.");
            }
            return new TokenResponse(
                    body.get("access_token").asText(),
                    body.hasNonNull("refresh_token") ? body.get("refresh_token").asText() : null,
                    body.hasNonNull("expires_in") ? body.get("expires_in").asLong() : 3600L,
                    body.hasNonNull("scope") ? body.get("scope").asText() : null,
                    null);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 400 || status.value() == 401) {
                // invalid_grant (code/refresh révoqué) ou identifiants client invalides
                throw new FitbitUnauthorizedException("Google a refusé la demande de token (" + status.value() + ")");
            }
            log.warn("Google token endpoint a renvoyé {} : {}", status, e.getResponseBodyAsString());
            throw new FitbitUnavailableException("Google a renvoyé " + status);
        } catch (ResourceAccessException e) {
            log.warn("OAuth Google injoignable : {}", e.getMessage());
            throw new FitbitUnavailableException("OAuth Google injoignable : " + e.getMessage());
        }
    }

    // ─────────────────────── Données santé (v4) ───────────────────────────

    /**
     * Pas quotidiens agrégés entre deux dates ({@code steps:dailyRollUp}).
     * Réponse : {@code rollupDataPoints[{civilStartTime,steps}]}.
     */
    public JsonNode rollUpDailySteps(String accessToken, LocalDate start, LocalDate end) {
        Map<String, Object> requestBody = Map.of(
                "range", Map.of(
                        "start", googleDate(start),
                        "end", googleDate(end)),
                "windowSizeDays", 1);
        return doPost(accessToken, "/v4/users/me/dataTypes/steps/dataPoints:dailyRollUp", requestBody);
    }

    /**
     * Sessions de sommeil depuis {@code from} (GET dataPoints filtrés).
     * Réponse : {@code dataPoints[{sleep:{interval,summary}}]}.
     */
    public JsonNode listSleep(String accessToken, LocalDate from) {
        // VÉRIFIER : syntaxe du filtre civil_start_time vs un compte réel.
        return doGet(accessToken, "/v4/users/me/dataTypes/sleep/dataPoints",
                "sleep.interval.civil_start_time >= \"" + from + "\"");
    }

    /**
     * Fréquence cardiaque de repos quotidienne depuis {@code from}.
     * Réponse : {@code dataPoints[{dailyRestingHeartRate:{beatsPerMinute}}]}.
     */
    public JsonNode listRestingHeartRate(String accessToken, LocalDate from) {
        // VÉRIFIER : champ de date 'dailyRestingHeartRate.date' vs un compte réel.
        return doGet(accessToken, "/v4/users/me/dataTypes/dailyRestingHeartRate/dataPoints",
                "dailyRestingHeartRate.date >= \"" + from + "\"");
    }

    /** Construit un objet {@code google.type.Date}. */
    private static Map<String, Integer> googleDate(LocalDate d) {
        return Map.of("year", d.getYear(), "month", d.getMonthValue(), "day", d.getDayOfMonth());
    }

    private JsonNode doGet(String accessToken, String path, String filter) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(path).queryParam("filter", filter).build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw mapHttpError("GET " + path, e);
        } catch (ResourceAccessException e) {
            log.warn("Google Health injoignable : {}", e.getMessage());
            throw new FitbitUnavailableException("Google Health injoignable : " + e.getMessage());
        }
    }

    private JsonNode doPost(String accessToken, String path, Object body) {
        try {
            return restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            throw mapHttpError("POST " + path, e);
        } catch (ResourceAccessException e) {
            log.warn("Google Health injoignable : {}", e.getMessage());
            throw new FitbitUnavailableException("Google Health injoignable : " + e.getMessage());
        }
    }

    private RuntimeException mapHttpError(String call, HttpClientErrorException e) {
        HttpStatusCode status = e.getStatusCode();
        if (status.value() == 401 || status.value() == 403) {
            return new FitbitUnauthorizedException("Token Google Health rejeté (" + status.value() + ")");
        }
        if (status.value() == 429) {
            return new FitbitUnavailableException("Quota Google Health dépassé, réessaie plus tard.");
        }
        log.warn("Google Health {} a renvoyé {} : {}", call, status, e.getResponseBodyAsString());
        return new FitbitUnavailableException("Google Health a renvoyé " + status);
    }

    /** Réponse du endpoint de token OAuth Google. {@code fitbitUserId} reste null. */
    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds,
                                String scope, String fitbitUserId) {}

    public static class FitbitUnavailableException extends RuntimeException {
        public FitbitUnavailableException(String msg) { super(msg); }
    }

    public static class FitbitUnauthorizedException extends RuntimeException {
        public FitbitUnauthorizedException(String msg) { super(msg); }
    }
}
