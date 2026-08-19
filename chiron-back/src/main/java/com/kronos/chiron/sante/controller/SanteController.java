package com.kronos.chiron.sante.controller;

import com.kronos.chiron.core.security.AuthenticatedUserService;
import com.kronos.chiron.fitbit.client.GoogleHealthDataType;
import com.kronos.chiron.sante.dto.SanteCardioHebdoDto;
import com.kronos.chiron.sante.dto.SanteFcJourDto;
import com.kronos.chiron.sante.dto.SanteFcPointDto;
import com.kronos.chiron.sante.dto.SanteJourDto;
import com.kronos.chiron.sante.dto.SanteResumeDto;
import com.kronos.chiron.sante.dto.SanteSommeilDto;
import com.kronos.chiron.sante.dto.SanteSyncEtatDto;
import com.kronos.chiron.sante.service.SanteDiagnosticService;
import com.kronos.chiron.sante.service.SanteQueryService;
import com.kronos.chiron.sante.service.SanteSyncService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/sante")
@RequiredArgsConstructor
public class SanteController {

    private final SanteQueryService santeQueryService;
    private final SanteSyncService santeSyncService;
    private final SanteDiagnosticService santeDiagnosticService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping("/resume")
    public ResponseEntity<SanteResumeDto> resume() {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.ok(santeQueryService.getResume(user));
    }

    @GetMapping("/jours")
    public ResponseEntity<List<SanteJourDto>> jours(@RequestParam(defaultValue = "30") int jours) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.ok(santeQueryService.getJours(user, jours));
    }

    @GetMapping("/frequence-cardiaque")
    public ResponseEntity<List<SanteFcPointDto>> frequenceCardiaqueJour(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.ok(santeQueryService.getFrequenceCardiaqueJour(user, date));
    }

    @GetMapping("/frequence-cardiaque/plage")
    public ResponseEntity<List<SanteFcJourDto>> frequenceCardiaquePlage(@RequestParam(defaultValue = "30") int jours) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.ok(santeQueryService.getFrequenceCardiaquePlage(user, jours));
    }

    @GetMapping("/sommeil")
    public ResponseEntity<List<SanteSommeilDto>> sommeil(@RequestParam(defaultValue = "30") int jours) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.ok(santeQueryService.getSommeil(user, jours));
    }

    @GetMapping("/cardio-hebdo")
    public ResponseEntity<List<SanteCardioHebdoDto>> cardioHebdo(@RequestParam(defaultValue = "12") int semaines) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.ok(santeQueryService.getCardioHebdo(user, semaines));
    }

    @GetMapping("/diagnostic")
    public ResponseEntity<JsonNode> diagnostic(@RequestParam GoogleHealthDataType type,
            @RequestParam(defaultValue = "3") int jours) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.ok(santeDiagnosticService.capturerBrut(user.getUsername(), type, jours));
    }

    @GetMapping("/diagnostic/types")
    public ResponseEntity<JsonNode> diagnosticTypes() {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.ok(santeDiagnosticService.listerTypesDisponibles(user.getUsername()));
    }

    @GetMapping("/diagnostic/slug")
    public ResponseEntity<JsonNode> diagnosticSlug(@RequestParam String slug,
            @RequestParam(defaultValue = "3") int jours) {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.ok(santeDiagnosticService.sonderSlug(user.getUsername(), slug, jours));
    }

    @GetMapping("/sync")
    public ResponseEntity<List<SanteSyncEtatDto>> syncEtat() {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        return ResponseEntity.ok(santeQueryService.getSyncEtat(user));
    }

    @PostMapping("/sync")
    public ResponseEntity<List<SanteSyncEtatDto>> forcerSync() {
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();
        santeSyncService.ensureBackfillAsync(user.getUsername());
        santeSyncService.syncRecent(user.getUsername(), 7);
        return ResponseEntity.ok(santeQueryService.getSyncEtat(user));
    }
}
