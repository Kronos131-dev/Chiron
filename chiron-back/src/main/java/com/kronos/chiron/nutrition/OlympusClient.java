package com.kronos.chiron.nutrition;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Client HTTP minimal vers l'API Olympus. Vague A : seulement l'authentification.
 * Les méthodes de lecture (daily-logs, analytics, profile) viendront en Vague B.
 */
@Component
@Slf4j
public class OlympusClient {

    private final RestClient restClient;

    public OlympusClient(@Value("${olympus.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    /**
     * Authentifie un utilisateur Olympus avec son pseudo et son mot de passe.
     * Renvoie un token JWT Olympus si succès. Renvoie null en cas d'identifiants invalides (401).
     * Toute autre erreur HTTP remonte une {@link OlympusUnavailableException}.
     */
    public AuthenticationResult authenticate(String pseudo, String password) {
        try {
            JsonNode body = restClient.post()
                    .uri("/api/v1/auth/login")
                    .body(Map.of("email", pseudo, "password", password))
                    .retrieve()
                    .body(JsonNode.class);

            if (body == null || !body.hasNonNull("token")) {
                throw new OlympusUnavailableException("Réponse Olympus invalide : token absent.");
            }

            String token = body.get("token").asText();
            JsonNode user = body.get("user");
            String olympusUsername = (user != null && user.hasNonNull("email")) ? user.get("email").asText() : pseudo;

            return new AuthenticationResult(token, olympusUsername);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 401 || status.value() == 403) {
                return null;
            }
            log.warn("Olympus authenticate a renvoyé {} : {}", status, e.getResponseBodyAsString());
            throw new OlympusUnavailableException("Olympus a renvoyé " + status);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            throw new OlympusUnavailableException("Olympus injoignable : " + e.getMessage());
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

    private JsonNode doGet(String token, String uri) {
        try {
            return restClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
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
            throw new OlympusUnavailableException("Olympus injoignable : " + e.getMessage());
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
