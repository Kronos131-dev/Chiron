package com.kronos.chiron.nutrition;

import tools.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Client HTTP vers l'API Olympus. La liaison de compte se fait une seule fois via
 * {@link #authenticate} : Olympus renvoie alors un token de liaison PERMANENT que
 * Chiron conserve et présente, dans l'en-tête {@code X-Integration-Token}, sur tous
 * les appels de lecture (journal, profil, analytics, planning, repas).
 */
@Component
@Slf4j
public class OlympusClient {

    private final RestClient restClient;
    private final String baseUrl;

    public OlympusClient(@Value("${olympus.base-url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
        log.info("OlympusClient configuré sur baseUrl={}", baseUrl);
    }

    /**
     * Lie le compte Olympus de l'utilisateur (pseudo + mot de passe) et récupère le
     * token de liaison PERMANENT. Ce token ne expire jamais : la liaison faite une
     * fois reste valable indéfiniment. Renvoie null si les identifiants sont
     * invalides (401) ; toute autre erreur HTTP remonte une {@link OlympusUnavailableException}.
     */
    public AuthenticationResult authenticate(String pseudo, String password) {
        try {
            JsonNode body = restClient.post()
                    .uri("/api/v1/integration/chiron/link")
                    .body(Map.of("email", pseudo, "password", password))
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null || !body.hasNonNull("linkToken")) {
                throw new OlympusUnavailableException("Réponse Olympus invalide : linkToken absent.");
            }

            String token = body.get("linkToken").asText();
            String olympusUsername = body.hasNonNull("olympusUsername")
                    ? body.get("olympusUsername").asText() : pseudo;

            return new AuthenticationResult(token, olympusUsername);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 401 || status.value() == 403) {
                return null;
            }
            log.warn("Olympus authenticate a renvoyé {} : {}", status, e.getResponseBodyAsString());
            throw new OlympusUnavailableException("Olympus a renvoyé " + status);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("Olympus injoignable sur {} : {}", baseUrl, e.getMessage());
            throw new OlympusUnavailableException("Olympus injoignable sur " + baseUrl + " : " + e.getMessage());
        }
    }

    /**
     * Récupère le journal nutritionnel pour une date donnée.
     */
    public JsonNode getDailyLog(String token, LocalDate date) {
        return doGet(token, "/api/v1/daily-logs/" + date.format(DateTimeFormatter.ISO_DATE));
    }

    /**
     * Récupère le profil Olympus de l'utilisateur (cibles, objectif, poids).
     */
    public JsonNode getUserProfile(String token) {
        return doGet(token, "/api/v1/users/profile");
    }

    /**
     * Agrégats nutritionnels sur une période (moyennes + série journalière).
     */
    public JsonNode getAnalytics(String token, LocalDate startDate, LocalDate endDate) {
        String uri = "/api/v1/analytics?startDate=" + startDate.format(DateTimeFormatter.ISO_DATE)
                + "&endDate=" + endDate.format(DateTimeFormatter.ISO_DATE);
        return doGet(token, uri);
    }

    /** Récupère l'emploi du temps de repas hebdomadaire planifié de l'utilisateur. */
    public JsonNode getWeeklyPlan(String token) {
        return doGet(token, "/api/v1/meal-plans/weekly");
    }

    /** Récupère les repas pré-enregistrés (modèles de repas) de l'utilisateur. */
    public JsonNode getMealPresets(String token) {
        return doGet(token, "/api/v1/meal-presets");
    }

    /**
     * Pousse le poids de l'utilisateur dans Olympus (seule écriture autorisée via le token
     * d'intégration). Olympus historise la métrique du jour. 401/403 → token rejeté.
     */
    public void pushWeight(String token, double weightKg) {
        try {
            restClient.post()
                    .uri("/api/v1/integration/chiron/weight")
                    .header("X-Integration-Token", token)
                    .body(Map.of("weightKg", weightKg))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 401 || status.value() == 403) {
                throw new OlympusUnauthorizedException("Token Olympus rejeté (" + status.value() + ")");
            }
            log.warn("Olympus POST /weight a renvoyé {} : {}", status, e.getResponseBodyAsString());
            throw new OlympusUnavailableException("Olympus a renvoyé " + status);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("Olympus injoignable sur {} : {}", baseUrl, e.getMessage());
            throw new OlympusUnavailableException("Olympus injoignable sur " + baseUrl + " : " + e.getMessage());
        }
    }

    private JsonNode doGet(String token, String uri) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header("X-Integration-Token", token)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 401 || status.value() == 403) {
                throw new OlympusUnauthorizedException("Token Olympus rejeté (" + status.value() + ")");
            }
            log.warn("Olympus GET {} a renvoyé {} : {}", uri, status, e.getResponseBodyAsString());
            throw new OlympusUnavailableException("Olympus a renvoyé " + status);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("Olympus injoignable sur {} : {}", baseUrl, e.getMessage());
            throw new OlympusUnavailableException("Olympus injoignable sur " + baseUrl + " : " + e.getMessage());
        }
    }

    public record AuthenticationResult(String token, String olympusUsername) {}

    public static class OlympusUnavailableException extends RuntimeException {
        public OlympusUnavailableException(String msg) { super(msg); }
    }

    public static class OlympusUnauthorizedException extends RuntimeException {
        public OlympusUnauthorizedException(String msg) { super(msg); }
    }
}
