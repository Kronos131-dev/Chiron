package com.kronos.chiron.controller;

import com.kronos.chiron.dto.ExerciceDefinitionDto;
import com.kronos.chiron.service.ExerciceDefinitionService;
import com.kronos.chiron.util.ExerciceDataImporter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exercices")
@RequiredArgsConstructor
public class ExerciceDefinitionController {

    private final ExerciceDefinitionService service;
    private final ExerciceDataImporter importer;

    @GetMapping
    public ResponseEntity<List<ExerciceDefinitionDto>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String muscle,
            @RequestParam(required = false) String equipement,
            @RequestParam(required = false) String difficulte) {
        return ResponseEntity.ok(service.search(q, muscle, equipement, difficulte));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExerciceDefinitionDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/{id}/image/{index}")
    public ResponseEntity<Resource> streamImage(@PathVariable Long id, @PathVariable int index) {
        Resource image = service.streamImage(id, index);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(image);
    }

    // Admin seulement — protégé dans SecurityConfig
    @PostMapping(value = "/import", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importDataset(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "imageDir", required = false) String imageDir) throws IOException {
        Path tmp = Files.createTempFile("exercicedb-", ".json");
        file.transferTo(tmp);
        int count = importer.importFromFile(tmp, imageDir != null ? Path.of(imageDir) : null);
        Files.deleteIfExists(tmp);
        return ResponseEntity.ok(Map.of("imported", count));
    }
}
