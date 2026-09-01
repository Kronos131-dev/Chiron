package com.kronos.chiron.fitbit.controller;

import com.kronos.chiron.fitbit.client.FitbitClient;
import com.kronos.chiron.fitbit.dto.FitbitDashboardDto;
import com.kronos.chiron.fitbit.dto.FitbitLinkStatus;
import com.kronos.chiron.fitbit.service.FitbitService;
import com.kronos.chiron.fitbit.service.FitbitSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/fitbit")
@RequiredArgsConstructor
@Slf4j
public class FitbitController {

    private final FitbitService fitbitService;
    private final FitbitSyncService fitbitSyncService;

    @GetMapping("/status")
    public ResponseEntity<FitbitLinkStatus> getStatus(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(fitbitService.getStatus(userDetails.getUsername()));
    }

    @GetMapping("/dashboard")
    public ResponseEntity<FitbitDashboardDto> getDashboard(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "7") int days) {
        FitbitDashboardDto dashboard = fitbitService.getDashboard(userDetails.getUsername(), days);
        if (dashboard.linked() && dashboard.dataAvailable()) {
            fitbitSyncService.syncEtatJournalier(userDetails.getUsername(), days);
        }
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/authorize-url")
    public ResponseEntity<Map<String, String>> getAuthorizeUrl(@AuthenticationPrincipal UserDetails userDetails) {
        String url = fitbitService.buildAuthorizationUrl(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("authorizeUrl", url));
    }

    @GetMapping("/callback")
    public ResponseEntity<String> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        if (error != null) {
            log.info("FITBIT_CALLBACK_DENIED error={}", error);
            return htmlPage(HttpStatus.OK, false,
                    "Connexion Fitbit annulée",
                    "Tu as refusé l'accès, ou Fitbit a renvoyé une erreur. Tu peux fermer cette page et réessayer depuis ton profil.");
        }
        if (code == null || state == null) {
            return htmlPage(HttpStatus.BAD_REQUEST, false,
                    "Requête invalide",
                    "Paramètres OAuth manquants. Ferme cette page et réessaie depuis ton profil.");
        }
        try {
            fitbitService.handleCallback(code, state);
            return htmlPage(HttpStatus.OK, true,
                    "Compte Fitbit connecté",
                    "Ton compte Fitbit est lié au Sanctuaire de Chiron. Tu peux fermer cette page et revenir dans l'application.");
        } catch (FitbitService.InvalidStateException e) {
            log.info("FITBIT_CALLBACK_INVALID_STATE");
            return htmlPage(HttpStatus.BAD_REQUEST, false,
                    "Session expirée",
                    "Ta demande de connexion a expiré. Ferme cette page et relance la connexion depuis ton profil.");
        } catch (FitbitClient.FitbitUnauthorizedException e) {
            log.info("FITBIT_CALLBACK_UNAUTHORIZED : {}", e.getMessage());
            return htmlPage(HttpStatus.BAD_REQUEST, false,
                    "Connexion refusée",
                    "Fitbit a refusé l'échange de jetons. Ferme cette page et réessaie depuis ton profil.");
        } catch (FitbitClient.FitbitUnavailableException e) {
            log.warn("FITBIT_CALLBACK_UNAVAILABLE : {}", e.getMessage());
            return htmlPage(HttpStatus.SERVICE_UNAVAILABLE, false,
                    "Fitbit indisponible",
                    "Fitbit est momentanément injoignable. Ferme cette page et réessaie plus tard.");
        } catch (RuntimeException e) {
            // WHY: cette page-ci s'ouvre dans le navigateur, pas dans l'application : une
            // exception qui s'échappe n'y devient pas du JSON, elle devient la page d'erreur
            // brute de Spring. C'est ce que l'athlète a vu quand la liaison a débordé la
            // colonne des scopes, sans que rien ne lui dise quoi faire.
            log.error("FITBIT_CALLBACK_ECHEC", e);
            return htmlPage(HttpStatus.INTERNAL_SERVER_ERROR, false,
                    "Connexion impossible",
                    "Le Sanctuaire n'a pas réussi à enregistrer la liaison. Ferme cette page et réessaie ; si cela recommence, le journal du serveur en garde la trace.");
        }
    }

    @DeleteMapping("/link")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal UserDetails userDetails) {
        fitbitService.unlink(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<String> htmlPage(HttpStatus status, boolean success, String title, String message) {
        String accent = success ? "#3fae6b" : "#c2532f";
        String icon = success ? "&#10003;" : "&#9888;";
        String html = """
                <!DOCTYPE html>
                <html lang="fr">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>%s</title>
                  <style>
                    body { margin:0; min-height:100vh; display:flex; align-items:center;
                           justify-content:center; font-family:system-ui,-apple-system,sans-serif;
                           background:#1a1410; color:#f3e9dc; }
                    .card { max-width:420px; padding:2.5rem 2rem; text-align:center;
                            background:#241c15; border-radius:16px; border:1px solid #3a2e22; }
                    .icon { font-size:3rem; color:%s; }
                    h1 { font-size:1.3rem; margin:1rem 0 0.5rem; }
                    p { color:#c9b7a4; line-height:1.5; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="icon">%s</div>
                    <h1>%s</h1>
                    <p>%s</p>
                  </div>
                </body>
                </html>
                """.formatted(title, accent, icon, title, message);
        return ResponseEntity.status(status)
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }
}
