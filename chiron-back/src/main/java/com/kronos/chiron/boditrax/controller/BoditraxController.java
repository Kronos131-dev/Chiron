package com.kronos.chiron.boditrax.controller;

import com.kronos.chiron.boditrax.service.BoditraxImportService;
import com.kronos.chiron.core.security.AuthenticatedUserService;
import com.kronos.chiron.utilisateur.model.Utilisateur;
import com.kronos.chiron.visbody.service.VisbodyImportService;
import com.kronos.chiron.visbody.service.VisbodyImportService.ImportResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/boditrax")
@RequiredArgsConstructor
public class BoditraxController {

    private final BoditraxImportService importService;
    private final AuthenticatedUserService authenticatedUserService;

    @PostMapping("/import")
    public ResponseEntity<ImportResult> importCsv(@RequestParam("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new ImportResult(VisbodyImportService.Outcome.INVALID, "Fichier vide."));
        }
        Utilisateur user = authenticatedUserService.getAuthenticatedUser();

        ImportResult result = importService.importCsv(file.getBytes(), user);
        HttpStatus status = switch (result.outcome()) {
            case IMPORTED -> HttpStatus.CREATED;
            case DUPLICATE -> HttpStatus.OK;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(result);
    }
}
