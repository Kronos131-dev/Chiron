package com.kronos.chiron.controller;

import com.kronos.chiron.entity.EtatJournalier;
import com.kronos.chiron.entity.Utilisateur;
import com.kronos.chiron.repository.UtilisateurRepository;
import com.kronos.chiron.service.RecoveryService;
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

    @PostMapping
    public ResponseEntity<EtatJournalierDto> upsert(@AuthenticationPrincipal UserDetails userDetails,
                                                     @RequestBody EtatJournalierDto body) {
        Utilisateur user = utilisateurRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
        LocalDate date = body.date() != null ? body.date() : LocalDate.now();
        EtatJournalier saved = recoveryService.upsert(
                user, date,
                body.sommeilHeures(), body.fatigue(), body.courbatures(),
                body.stress(), body.energie(), body.notes()
        );
        return ResponseEntity.ok(toDto(saved));
    }

    @GetMapping("/recent")
    public ResponseEntity<List<EtatJournalierDto>> recent(@AuthenticationPrincipal UserDetails userDetails,
                                                           @RequestParam(defaultValue = "7") int days) {
        Utilisateur user = utilisateurRepository.findByUsername(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));
        List<EtatJournalierDto> dtos = recoveryService.getRecent(user, days).stream().map(this::toDto).toList();
        return ResponseEntity.ok(dtos);
    }

    private EtatJournalierDto toDto(EtatJournalier e) {
        return new EtatJournalierDto(
                e.getDate(), e.getSommeilHeures(),
                e.getFatigue(), e.getCourbatures(), e.getStress(), e.getEnergie(),
                e.getNotes()
        );
    }

    public record EtatJournalierDto(
            LocalDate date,
            Double sommeilHeures,
            Integer fatigue,
            Integer courbatures,
            Integer stress,
            Integer energie,
            String notes
    ) {}
}
