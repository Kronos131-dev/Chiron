package com.kronos.chiron.exportation.controller;

import com.kronos.chiron.core.security.AuthenticatedUserService;
import com.kronos.chiron.exportation.dto.ExportProgrammeDto;
import com.kronos.chiron.exportation.dto.ExportProgrammeRequest;
import com.kronos.chiron.exportation.dto.FichierExporte;
import com.kronos.chiron.exportation.service.ExportProgrammeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/exports")
@RequiredArgsConstructor
public class ExportProgrammeController {

    private static final MediaType XLSX = MediaType
            .parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final ExportProgrammeService exportProgrammeService;
    private final AuthenticatedUserService authenticatedUserService;

    @GetMapping
    public ResponseEntity<List<ExportProgrammeDto>> lister() {
        return ResponseEntity.ok(exportProgrammeService.lister(authenticatedUserService.getAuthenticatedUsername()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExportProgrammeDto> detail(@PathVariable Long id) {
        return ResponseEntity
                .ok(exportProgrammeService.detail(authenticatedUserService.getAuthenticatedUsername(), id));
    }

    @PostMapping
    public ResponseEntity<ExportProgrammeDto> creer(@Valid @RequestBody ExportProgrammeRequest requete) {
        ExportProgrammeDto export = exportProgrammeService.creer(authenticatedUserService.getAuthenticatedUsername(),
                requete);
        return ResponseEntity.status(HttpStatus.CREATED).body(export);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExportProgrammeDto> modifier(@PathVariable Long id,
            @Valid @RequestBody ExportProgrammeRequest requete) {
        return ResponseEntity.ok(exportProgrammeService.modifier(authenticatedUserService.getAuthenticatedUsername(),
                id, requete));
    }

    @GetMapping("/{id}/telecharger")
    public ResponseEntity<byte[]> telecharger(@PathVariable Long id) {
        FichierExporte fichier = exportProgrammeService.telecharger(authenticatedUserService.getAuthenticatedUsername(),
                id);
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fichier.nom(), StandardCharsets.UTF_8).build()
                                .toString())
                .body(fichier.contenu());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        exportProgrammeService.supprimer(authenticatedUserService.getAuthenticatedUsername(), id);
        return ResponseEntity.noContent().build();
    }
}
