package com.kronos.chiron.fitbit.client;

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

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Component
@Slf4j
public class FitbitClient {

    private final RestClient restClient;
    private final String tokenUrl;
    private final String redirectUri;
    private final String clientId;
    private final String clientSecret;

    public FitbitClient(
            RestClient.Builder restClientBuilder,
            @Value("${fitbit.api-base-url}") String apiBaseUrl,
            @Value("${fitbit.token-url}") String tokenUrl,
            @Value("${fitbit.redirect-uri}") String redirectUri,
            @Value("${fitbit.client-id}") String clientId,
            @Value("${fitbit.client-secret}") String clientSecret) {
        this.tokenUrl = tokenUrl;
        this.redirectUri = redirectUri;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.restClient = restClientBuilder.baseUrl(apiBaseUrl).build();
        log.info("FitbitClient configuré (Google Health API, apiBaseUrl={})", apiBaseUrl);
    }

    public TokenResponse exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);
        return postToken(form);
    }

    public TokenResponse refresh(String refreshToken) {
        MultiValueMap<String, String> form = baseForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return postToken(form);
    }

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
                throw new FitbitUnauthorizedException("Google a refusé la demande de token (" + status.value() + ")");
            }
            log.warn("Google token endpoint a renvoyé {} : {}", status, e.getResponseBodyAsString());
            throw new FitbitUnavailableException("Google a renvoyé " + status);
        } catch (ResourceAccessException e) {
            log.warn("OAuth Google injoignable : {}", e.getMessage());
            throw new FitbitUnavailableException("OAuth Google injoignable : " + e.getMessage());
        }
    }

    public JsonNode rollUpDailySteps(String accessToken, LocalDate start, LocalDate endInclusive) {
        // WHY: range est un CivilTimeInterval (start/end = CivilDateTime = {date:{year,month,day}}),
        // pas une paire de google.type.Date à plat, et end est exclusif : on décale d'un jour ici
        // pour que les appelants continuent de raisonner en bornes inclusives.
        Map<String, Object> requestBody = Map.of(
                "range", Map.of(
                        "start", civilDateTime(start),
                        "end", civilDateTime(endInclusive.plusDays(1))),
                "windowSizeDays", 1);
        return doPost(accessToken, "/v4/users/me/dataTypes/steps/dataPoints:dailyRollUp", requestBody);
    }

    public JsonNode listSleep(String accessToken, LocalDate from) {
        return doGet(accessToken, "/v4/users/me/dataTypes/sleep/dataPoints",
                "sleep.interval.civil_end_time >= \"" + from + "\"");
    }

    public JsonNode listRestingHeartRate(String accessToken, LocalDate from) {
        // WHY: le dataType s'appelle 'daily-resting-heart-rate' dans le path (kebab-case) et
        // 'daily_resting_heart_rate' dans le filtre (snake_case) ; 'dailyRestingHeartRate'
        // n'existe sous aucune forme dans l'API Google Health.
        return doGet(accessToken, "/v4/users/me/dataTypes/daily-resting-heart-rate/dataPoints",
                "daily_resting_heart_rate.date >= \"" + from + "\"");
    }

    public JsonNode listDataPoints(String accessToken, GoogleHealthDataType type, LocalDate from, String pageToken) {
        String filtre = type.hasFiltre() ? type.filterFrom(from) : null;
        return doGet(accessToken, type.dataPointsPath(), filtre, pageToken);
    }

    // WHY: sert à sonder un type de donnée que l'enum ne connaît pas encore. Les slugs de
    // l'API ne sont documentés nulle part et se découvrent en essayant ; sans ce chemin
    // brut, chaque hypothèse de nom coûterait un déploiement. Le filtre suit la convention
    // constante de l'API : le slug en snake_case suivi de « .date ».
    public JsonNode listerTypesDeDonnees(String accessToken) {
        return doGet(accessToken, "/v4/users/me/dataTypes", null, null);
    }

    public JsonNode listDataPointsBrut(String accessToken, String slug, LocalDate from, String pageToken,
            boolean avecFiltre) {
        // WHY: le filtre est fabriqué par convention et n'a jamais été vérifié pour un slug
        // inconnu. Pouvoir l'omettre sépare les deux causes d'un 400 : un champ de filtre
        // mal nommé répond alors normalement, un type inexistant échoue dans les deux cas.
        String filtre = avecFiltre ? slug.replace('-', '_') + ".date >= \"" + from + "\"" : null;
        return doGet(accessToken, "/v4/users/me/dataTypes/" + slug + "/dataPoints", filtre, pageToken);
    }

    public JsonNode dailyRollUp(String accessToken, GoogleHealthDataType type, LocalDate start,
            LocalDate endInclusive) {
        Map<String, Object> requestBody = Map.of(
                "range", Map.of(
                        "start", civilDateTime(start),
                        "end", civilDateTime(endInclusive.plusDays(1))),
                "windowSizeDays", 1);
        return doPost(accessToken, type.dataPointsPath() + ":dailyRollUp", requestBody);
    }

    // WHY: windowSize n'a jamais été confronté à un compte réel. Suivi ici la convention
    // JSON habituelle de google.protobuf.Duration ("300s"), cohérente avec le reste de
    // l'API (CivilDateTime, Timestamp RFC3339) mais non documentée explicitement pour ce
    // champ précis — à confirmer avant de s'appuyer dessus en production.
    public JsonNode rollUp(String accessToken, GoogleHealthDataType type, Instant startUtc, Instant endUtc,
            int windowSizeSeconds) {
        Map<String, Object> requestBody = Map.of(
                "range", Map.of(
                        "startTime", startUtc.toString(),
                        "endTime", endUtc.toString()),
                "windowSize", windowSizeSeconds + "s");
        return doPost(accessToken, type.dataPointsPath() + ":rollUp", requestBody);
    }

    public JsonNode pousserSeance(String accessToken, Instant startUtc, String startUtcOffset,
            Instant endUtc, String endUtcOffset, String exerciseType, String displayName, String notes) {
        Map<String, Object> requestBody = Map.of(
                "interval", Map.of(
                        "startTime", startUtc.toString(),
                        "startUtcOffset", startUtcOffset,
                        "endTime", endUtc.toString(),
                        "endUtcOffset", endUtcOffset),
                "exerciseType", exerciseType,
                "displayName", displayName,
                "notes", notes);
        return doPost(accessToken, GoogleHealthDataType.EXERCISE.dataPointsPath(), requestBody);
    }

    private static Map<String, Object> civilDateTime(LocalDate d) {
        return Map.of("date", Map.of("year", d.getYear(), "month", d.getMonthValue(), "day", d.getDayOfMonth()));
    }

    private static final int DEBUG_LOG_MAX_LENGTH = 2000;
    private static final int ERREUR_MAX_LENGTH = 400;

    private static String tronquer(String corps) {
        if (corps == null || corps.isBlank()) return "(corps vide)";
        String plat = corps.replaceAll("\\s+", " ").trim();
        return plat.length() > ERREUR_MAX_LENGTH ? plat.substring(0, ERREUR_MAX_LENGTH) + "…" : plat;
    }

    private static String abbreviate(JsonNode response) {
        String json = String.valueOf(response);
        return json.length() > DEBUG_LOG_MAX_LENGTH ? json.substring(0, DEBUG_LOG_MAX_LENGTH) + "…" : json;
    }

    private JsonNode doGet(String accessToken, String path, String filter) {
        return doGet(accessToken, path, filter, null);
    }

    private JsonNode doGet(String accessToken, String path, String filter, String pageToken) {
        try {
            JsonNode response = restClient.get()
                    .uri(uriBuilder -> {
                        uriBuilder.path(path).queryParam("filter", filter);
                        if (pageToken != null && !pageToken.isBlank()) {
                            uriBuilder.queryParam("pageToken", pageToken);
                        }
                        return uriBuilder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
            log.debug("Google Health GET {} → {}", path, abbreviate(response));
            return response;
        } catch (HttpClientErrorException e) {
            throw mapHttpError("GET " + path, e);
        } catch (ResourceAccessException e) {
            log.warn("Google Health injoignable : {}", e.getMessage());
            throw new FitbitUnavailableException("Google Health injoignable : " + e.getMessage());
        }
    }

    private JsonNode doPost(String accessToken, String path, Object body) {
        try {
            JsonNode response = restClient.post()
                    .uri(path)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            log.debug("Google Health POST {} → {}", path, abbreviate(response));
            return response;
        } catch (HttpClientErrorException e) {
            throw mapHttpError("POST " + path, e);
        } catch (ResourceAccessException e) {
            log.warn("Google Health injoignable : {}", e.getMessage());
            throw new FitbitUnavailableException("Google Health injoignable : " + e.getMessage());
        }
    }

    private RuntimeException mapHttpError(String call, HttpClientErrorException e) {
        HttpStatusCode status = e.getStatusCode();
        // WHY: 401 = access token rejeté, vrai problème de token. 403 = requête interdite :
        // API Google Health non activée sur le projet, scope OAuth absent, ou endpoint erroné.
        // Ce n'est PAS une expiration et ne doit jamais entraîner la suppression de la liaison.
        if (status.value() == 401) {
            return new FitbitUnauthorizedException("Token Google Health rejeté (401)");
        }
        if (status.value() == 403) {
            log.warn(
                    "Google Health {} a renvoyé 403 (accès refusé — API activée sur le projet ? scopes accordés ?) : {}",
                    call, e.getResponseBodyAsString());
            return new FitbitUnavailableException("Accès Google Health refusé (403)");
        }
        if (status.value() == 429) {
            return new FitbitUnavailableException("Quota Google Health dépassé, réessaie plus tard.");
        }
        // WHY: le corps du refus porte l'explication — champ de filtre inconnu, type de
        // donnée inexistant — et il n'était visible que dans les journaux du serveur. Le
        // remonter dans le message rend le diagnostic lisible depuis le navigateur, et
        // renseigne aussi dernierMessage quand la synchro échoue.
        String corps = e.getResponseBodyAsString();
        log.warn("Google Health {} a renvoyé {} : {}", call, status, corps);
        return new FitbitUnavailableException("Google Health a renvoyé " + status + " : " + tronquer(corps));
    }

    public record TokenResponse(String accessToken, String refreshToken, long expiresInSeconds,
            String scope, String fitbitUserId) {
    }

    public static class FitbitUnavailableException extends RuntimeException {
        public FitbitUnavailableException(String msg) {
            super(msg);
        }
    }

    public static class FitbitUnauthorizedException extends RuntimeException {
        public FitbitUnauthorizedException(String msg) {
            super(msg);
        }
    }
}
