package com.kronos.chiron.controller;

import com.kronos.chiron.nutrition.NutritionLinkStatus;
import com.kronos.chiron.nutrition.NutritionService;
import com.kronos.chiron.nutrition.OlympusClient;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/nutrition")
@RequiredArgsConstructor
public class NutritionController {

    private final NutritionService nutritionService;

    @GetMapping("/status")
    public ResponseEntity<NutritionLinkStatus> getStatus(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(nutritionService.getStatus(userDetails.getUsername()));
    }

    @PostMapping("/link")
    public ResponseEntity<?> link(@AuthenticationPrincipal UserDetails userDetails,
                                  @RequestBody LinkRequest req) {
        try {
            NutritionLinkStatus status = nutritionService.link(userDetails.getUsername(), req.pseudo(), req.password());
            return ResponseEntity.ok(status);
        } catch (NutritionService.InvalidCredentialsException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse("Identifiants Olympus invalides."));
        } catch (OlympusClient.OlympusUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse("Le service Olympus est indisponible. Réessaie plus tard."));
        }
    }

    @DeleteMapping("/link")
    public ResponseEntity<Void> unlink(@AuthenticationPrincipal UserDetails userDetails) {
        nutritionService.unlink(userDetails.getUsername());
        return ResponseEntity.noContent().build();
    }

    public record LinkRequest(@NotBlank String pseudo, @NotBlank String password) {}
    public record ErrorResponse(String message) {}
}
