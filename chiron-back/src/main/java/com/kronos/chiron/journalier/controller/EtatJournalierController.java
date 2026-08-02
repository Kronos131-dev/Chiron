package com.kronos.chiron.journalier.controller;

import java.time.Clock;

import static com.kronos.chiron.core.exceptions.ErrorFactory.notFound;

import com.kronos.chiron.journalier.model.EtatJournalier;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.journalier.service.RecoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/etat-journalier")
@RequiredArgsConstructor
public class EtatJournalierController {

    private final RecoveryService recoveryService;
    private final UtilisateurRepository utilisateurRepository;

    private final Clock clock;
    @PostMapping
    public ResponseEntity<EtatJournalierDto> upsert(@AuthenticationPrincipal UserDetails userDetails,
            @RequestBody EtatJournalierDto body) {
        Utilisateur user = utilisateurRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
        LocalDate date = body.date() != null ? body.date() : LocalDate.now(clock);
        EtatJournalier saved = recoveryService.upsert(
                user, date,
                body.sommeilHeures(), body.fatigue(), body.courbatures(),
                body.stress(), body.energie(), body.notes());
        return ResponseEntity.ok(toDto(saved));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<EtatJournalierDto>> recent(@AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "7") int days) {
        Utilisateur user = utilisateurRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> notFound("Utilisateur introuvable"));
        List<EtatJournalierDto> dtos = recoveryService.getRecent(user, days).stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    private EtatJournalierDto toDto(EtatJournalier e) {
        return new EtatJournalierDto(
                e.getDate(), e.getSommeilHeures(),
                e.getFatigue(), e.getCourbatures(), e.getStress(), e.getEnergie(),
                e.getNotes());
    }

    public record EtatJournalierDto(
            LocalDate date,
            Double sommeilHeures,
            Integer fatigue,
            Integer courbatures,
            Integer stress,
            Integer energie,
            String notes) {
    }
}
