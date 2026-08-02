package com.kronos.chiron.boditrax.controller;

import com.kronos.chiron.boditrax.BoditraxImportService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.utilisateur.persistence.UtilisateurRepository;
import com.kronos.chiron.visbody.VisbodyImportService;
import com.kronos.chiron.visbody.VisbodyImportService.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/boditrax")
@RequiredArgsConstructor
public class BoditraxController {

    private final BoditraxImportService importService;
    private final UtilisateurRepository utilisateurRepo;

    @PostMapping("/import")
    public ResponseEntity<ImportResult> importCsv(@RequestParam("file") MultipartFile file,
            Authentication auth) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ImportResult(VisbodyImportService.Outcome.INVALID, "Fichier vide."));
        }
        Utilisateur user = utilisateurRepo.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Utilisateur courant introuvable"));

        ImportResult result = importService.importCsv(file.getBytes(), user);
        HttpStatus status = switch (result.outcome()) {
            case IMPORTED -> HttpStatus.CREATED;
            case DUPLICATE -> HttpStatus.OK;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(result);
    }
}
