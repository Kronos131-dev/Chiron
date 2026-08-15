package com.kronos.chiron.visbody.controller;

import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.visbody.model.BodyCompositionRecord;
import com.kronos.chiron.visbody.persistence.BodyCompositionRecordRepository;
import com.kronos.chiron.visbody.service.VisbodyImportService;
import com.kronos.chiron.visbody.service.VisbodyImportService.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/visbody")
@RequiredArgsConstructor
public class VisbodyController {

    private final VisbodyImportService importService;
    private final BodyCompositionRecordRepository recordRepo;
    private final UtilisateurRepository utilisateurRepo;

    @PostMapping("/import")
    public ResponseEntity<ImportResult> importPdf(@RequestParam("file") MultipartFile file,
            Authentication auth) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ImportResult(VisbodyImportService.Outcome.INVALID, "Fichier vide."));
        }
        Utilisateur user = utilisateurRepo.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable"));

        ImportResult result = importService.importForUser(file.getBytes(), user);
        HttpStatus status = switch (result.outcome()) {
            case IMPORTED -> HttpStatus.CREATED;
            case DUPLICATE -> HttpStatus.OK;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(result);
    }

    @GetMapping("/records")
    public ResponseEntity<List<BodyCompositionRecord>> records(Authentication auth) {
        Utilisateur user = utilisateurRepo.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable"));
        return ResponseEntity.ok(recordRepo.findByUtilisateurOrderByMesureLeAsc(user));
    }
}
