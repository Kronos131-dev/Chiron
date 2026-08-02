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
                    ? body.get("olympusUsername").asText()
                    : pseudo;

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

    public JsonNode getDailyLog(String token, LocalDate date) {
        return doGet(token, "/api/v1/daily-logs/" + date.format(DateTimeFormatter.ISO_DATE));
    }

    public JsonNode getUserProfile(String token) {
        return doGet(token, "/api/v1/users/profile");
    }

    public JsonNode getAnalytics(String token, LocalDate startDate, LocalDate endDate) {
        String uri = "/api/v1/analytics?startDate=" + startDate.format(DateTimeFormatter.ISO_DATE)
                + "&endDate=" + endDate.format(DateTimeFormatter.ISO_DATE);
        return doGet(token, uri);
    }

    public JsonNode getWeeklyPlan(String token) {
        return doGet(token, "/api/v1/meal-plans/weekly");
    }

    public JsonNode getMealPresets(String token) {
        return doGet(token, "/api/v1/meal-presets");
    }

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

    public record AuthenticationResult(String token, String olympusUsername) {
    }

    public static class OlympusUnavailableException extends RuntimeException {
        public OlympusUnavailableException(String msg) {
            super(msg);
        }
    }

    public static class OlympusUnauthorizedException extends RuntimeException {
        public OlympusUnauthorizedException(String msg) {
            super(msg);
        }
    }
}
