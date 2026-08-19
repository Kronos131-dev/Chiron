package com.kronos.chiron.sante.service.impl;

import com.kronos.chiron.fitbit.client.FitbitClient;
import com.kronos.chiron.fitbit.client.GoogleHealthDataType;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.sante.service.SanteDiagnosticService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.function.Supplier;

import static com.kronos.chiron.core.exceptions.ErrorFactory.badRequest;

@Service
@RequiredArgsConstructor
public class SanteDiagnosticServiceImpl implements SanteDiagnosticService {

    private static final int JOURS_MAX = 30;
    private static final java.util.regex.Pattern SLUG_VALIDE = java.util.regex.Pattern
            .compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final int FENETRE_FC_SECONDES = 300;

    private final FitbitService fitbitService;
    private final FitbitClient fitbitClient;
    private final Clock clock;

    @Override
    public JsonNode capturerBrut(String chironUsername, GoogleHealthDataType type, int jours,
            boolean sousJournalier, int fenetreSecondes) {
        String token = jetonOuErreur(chironUsername);
        int n = Math.max(1, Math.min(jours, JOURS_MAX));
        LocalDate aujourdhui = LocalDate.now(clock);
        LocalDate depuis = aujourdhui.minusDays(n - 1L);

        if (sousJournalier && type.operation() == GoogleHealthDataType.Operation.DAILY_ROLLUP) {
            int fenetre = fenetreSecondes > 0 ? fenetreSecondes : FENETRE_FC_SECONDES;
            return fitbitClient.rollUp(token, type, debutDe(depuis), debutDe(depuis.plusDays(1)), fenetre);
        }

        return switch (type.operation()) {
            case LIST -> fitbitClient.listDataPoints(token, type, depuis, null);
            case DAILY_ROLLUP -> fitbitClient.dailyRollUp(token, type, depuis, aujourdhui);
            case ROLLUP -> fitbitClient.rollUp(token, type, debutDe(depuis), debutDe(depuis.plusDays(1)),
                    FENETRE_FC_SECONDES);
        };
    }

    @Override
    public JsonNode sonderSlug(String chironUsername, String slug, int jours, boolean avecFiltre) {
        if (slug == null || !SLUG_VALIDE.matcher(slug).matches()) {
            throw badRequest("Slug invalide : attendu des minuscules, des chiffres et des tirets.");
        }
        String token = jetonOuErreur(chironUsername);
        int n = Math.max(1, Math.min(jours, JOURS_MAX));
        LocalDate depuis = LocalDate.now(clock).minusDays(n - 1L);
        return sansPropager(slug, () -> fitbitClient.listDataPointsBrut(token, slug, depuis, null, avecFiltre));
    }

    @Override
    public JsonNode listerTypesDisponibles(String chironUsername) {
        String token = jetonOuErreur(chironUsername);
        return sansPropager("dataTypes", () -> fitbitClient.listerTypesDeDonnees(token));
    }

    // WHY: une sonde sert à savoir ce que Google répond. Laisser l'exception remonter
    // produisait un 500 opaque, alors que le refus lui-même est le résultat cherché : 404
    // pour un type qui n'existe pas, 403 pour un scope absent. L'erreur est donc renvoyée
    // comme une réponse lisible, et seul le diagnostic emprunte ce chemin.
    private JsonNode sansPropager(String cible, Supplier<JsonNode> appel) {
        try {
            return appel.get();
        } catch (FitbitClient.FitbitUnavailableException | FitbitClient.FitbitUnauthorizedException e) {
            ObjectNode reponse = JsonNodeFactory.instance.objectNode();
            reponse.put("cible", cible);
            reponse.put("erreur", e.getMessage());
            return reponse;
        }
    }

    private String jetonOuErreur(String chironUsername) {
        try {
            return fitbitService.getValidToken(chironUsername);
        } catch (FitbitService.NotLinkedException e) {
            throw badRequest("Aucun compte Google Health lié à cet utilisateur.");
        } catch (FitbitService.ExpiredException e) {
            throw badRequest("Le lien Google Health a expiré, il faut le reconnecter.");
        }
    }

    private Instant debutDe(LocalDate date) {
        ZoneId zone = clock.getZone();
        return date.atStartOfDay(zone).toInstant();
    }
}
