package com.kronos.chiron.controller;

import com.kronos.chiron.nutrition.NutritionLinkStatus;
import com.kronos.chiron.nutrition.NutritionService;
import com.kronos.chiron.nutrition.OlympusClient;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nutrition")
@RequiredArgsConstructor
@Slf4j
public class NutritionController {

    private final NutritionService nutritionService;

    @GetMapping("/status")
    public ResponseEntity<NutritionLinkStatus> getStatus(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(nutritionService.getStatus(userDetails.getUsername()));
    }

    @PostMapping("/link")
    public ResponseEntity<?> link(@AuthenticationPrincipal UserDetails userDetails,
                                  @RequestBody LinkRequest req) {
        log.info("NUTRITION_LINK_CALLED principal={} pseudoLen={}",
                userDetails != null ? userDetails.getUsername() : "<null>",
                req != null && req.pseudo() != null ? req.pseudo().length() : -1);
        try {
            NutritionLinkStatus status = nutritionService.link(userDetails.getUsername(), req.pseudo(), req.password());
            return ResponseEntity.ok(status);
        } catch (NutritionService.InvalidCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Identifiants Olympus invalides."));
        } catch (OlympusClient.OlympusUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("Olympus indisponible : " + e.getMessage()));
        }
    }

    @DeleteMapping("/link")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal UserDetails userDetails) {
        nutritionService.unlink(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    /**
     * Remet le token de liaison Olympus de l'utilisateur pour l'« entrée directe » dans la
     * PWA Olympus : le front ouvre {@code /olympus/#ctk=<token>}, et la PWA échange ce token
     * contre une session Olympus complète. Réservé à l'utilisateur authentifié (son propre
     * token) ; renvoie 409 si le compte n'est pas lié ou si le lien doit être refait.
     */
    @GetMapping("/handoff")
    public ResponseEntity<?> handoff(@AuthenticationPrincipal UserDetails userDetails) {
        try {
            String token = nutritionService.getValidToken(userDetails.getUsername());
            return ResponseEntity.ok(new HandoffResponse(token));
        } catch (NutritionService.NotLinkedException | NutritionService.ExpiredException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("Compte Olympus non lié."));
        }
    }

    public record LinkRequest(@NotBlank String pseudo, @NotBlank String password) {}
    public record ErrorResponse(String message) {}
    public record HandoffResponse(String token) {}
}
