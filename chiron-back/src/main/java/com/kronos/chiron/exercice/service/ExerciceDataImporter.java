package com.kronos.chiron.exercice.service;

import static com.kronos.chiron.core.exceptions.ErrorFactory.badRequest;

import com.kronos.chiron.exercice.model.ExerciceDefinition;
import com.kronos.chiron.exercice.model.MuscleGroup;
import com.kronos.chiron.exercice.model.NiveauDifficulte;
import com.kronos.chiron.exercice.model.TypeEquipement;

import com.kronos.chiron.core.exceptions.ChironTechnicalException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.kronos.chiron.exercice.persistence.ExerciceDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExerciceDataImporter {

    private static final String FR_NAMES_RESOURCE = "/exercices/noms-fr.json";

    private final ExerciceDefinitionRepository repository;
    private final JsonMapper objectMapper;

    @Value("${chiron.uploads-dir:./uploads/images}")
    private String uploadsDir;

    static final Map<String, NiveauDifficulte> LEVEL_MAP = Map.of(
            "beginner", NiveauDifficulte.DEBUTANT,
            "intermediate", NiveauDifficulte.INTERMEDIAIRE,
            "expert", NiveauDifficulte.AVANCE);

    static final Map<String, TypeEquipement> EQUIPMENT_MAP = Map.ofEntries(
            Map.entry("barbell", TypeEquipement.BARRE),
            Map.entry("dumbbell", TypeEquipement.HALTERES),
            Map.entry("cable", TypeEquipement.POULIE),
            Map.entry("machine", TypeEquipement.MACHINE),
            Map.entry("kettlebells", TypeEquipement.KETTLEBELL),
            Map.entry("bands", TypeEquipement.ELASTIQUE),
            Map.entry("body only", TypeEquipement.POIDS_DU_CORPS),
            Map.entry("e-z curl bar", TypeEquipement.BARRE),
            Map.entry("medicine ball", TypeEquipement.AUTRE),
            Map.entry("exercise ball", TypeEquipement.AUTRE),
            Map.entry("foam roll", TypeEquipement.AUTRE),
            Map.entry("other", TypeEquipement.AUTRE));

    static final Map<String, MuscleGroup> MUSCLE_MAP = Map.ofEntries(
            Map.entry("abdominals", MuscleGroup.ABDOMINAUX),
            Map.entry("abductors", MuscleGroup.ABDUCTEURS),
            Map.entry("adductors", MuscleGroup.ADDUCTEURS),
            Map.entry("biceps", MuscleGroup.BICEPS),
            Map.entry("calves", MuscleGroup.MOLLETS),
            Map.entry("chest", MuscleGroup.PECTORAUX),
            Map.entry("forearms", MuscleGroup.AVANT_BRAS),
            Map.entry("glutes", MuscleGroup.FESSIERS),
            Map.entry("hamstrings", MuscleGroup.ISCHIO_JAMBIERS),
            Map.entry("lats", MuscleGroup.DOS),
            Map.entry("lower back", MuscleGroup.LOMBAIRES),
            Map.entry("middle back", MuscleGroup.DOS),
            Map.entry("neck", MuscleGroup.TRAPEZES),
            Map.entry("quadriceps", MuscleGroup.QUADRICEPS),
            Map.entry("shoulders", MuscleGroup.EPAULES),
            Map.entry("traps", MuscleGroup.TRAPEZES),
            Map.entry("triceps", MuscleGroup.TRICEPS));

    static final Map<String, String> FR_NAMES = loadFrenchNames();

    private static Map<String, String> loadFrenchNames() {
        try (InputStream in = ExerciceDataImporter.class.getResourceAsStream(FR_NAMES_RESOURCE)) {
            if (in == null) {
                throw new ChironTechnicalException("Ressource introuvable : " + FR_NAMES_RESOURCE);
            }
            return Map.copyOf(JsonMapper.builder().build()
                    .readValue(in, new TypeReference<LinkedHashMap<String, String>>() {
                    }));
        } catch (IOException e) {
            throw new ChironTechnicalException("Lecture de " + FR_NAMES_RESOURCE + " impossible", e);
        }
    }

    @Transactional
    public int importFromFile(Path jsonFile, Path imageSourceDir) throws IOException {
        JsonNode root = objectMapper.readTree(jsonFile.toFile());
        if (!root.isArray()) throw badRequest("Le JSON doit être un tableau d'exercices");

        Path exercicesDir = Paths.get(uploadsDir).resolve("exercices");
        Files.createDirectories(exercicesDir);

        int count = 0;
        for (JsonNode node : root) {
            try {
                count += processExercise(node, exercicesDir, imageSourceDir) ? 1 : 0;
            } catch (Exception e) {
                log.warn("Import ignoré pour '{}': {}", node.path("name").asText(), e.getMessage());
            }
        }
        log.info("Import terminé : {} exercices importés/mis à jour sur {}", count, root.size());
        return count;
    }

    private boolean processExercise(JsonNode node, Path exercicesDir, Path imageSourceDir) throws IOException {
        String externalId = node.path("id").asText(null);
        if (externalId == null || externalId.isBlank()) return false;
        if (repository.findByExternalId(externalId).isPresent()) return false;

        String nameEn = node.path("name").asText(null);
        if (nameEn == null || nameEn.isBlank()) return false;

        String nomFr = FR_NAMES.get(nameEn.toLowerCase().trim());

        MuscleGroup musclePrincipal = null;
        JsonNode primaryMuscles = node.path("primaryMuscles");
        if (primaryMuscles.isArray() && primaryMuscles.size() > 0) {
            musclePrincipal = MUSCLE_MAP.get(primaryMuscles.get(0).asText("").toLowerCase());
        }

        List<MuscleGroup> musclesSecondaires = new ArrayList<>();
        for (JsonNode m : node.path("secondaryMuscles")) {
            MuscleGroup mg = MUSCLE_MAP.get(m.asText("").toLowerCase());
            if (mg != null && mg != musclePrincipal) musclesSecondaires.add(mg);
        }

        TypeEquipement equipement = EQUIPMENT_MAP.getOrDefault(
                node.path("equipment").asText("").toLowerCase(), TypeEquipement.AUTRE);

        NiveauDifficulte difficulte = LEVEL_MAP.getOrDefault(
                node.path("level").asText("").toLowerCase(), NiveauDifficulte.INTERMEDIAIRE);

        StringBuilder desc = new StringBuilder();
        for (JsonNode instr : node.path("instructions")) {
            if (!desc.isEmpty()) desc.append("\n");
            desc.append(instr.asText());
        }

        String imageFolderName = copyImages(externalId, node.path("images"), exercicesDir, imageSourceDir);

        ExerciceDefinition def = ExerciceDefinition.builder()
                .externalId(externalId)
                .nomEn(nameEn)
                .nomFr(nomFr)
                .descriptionEn(desc.isEmpty() ? null : desc.toString())
                .gifPath(imageFolderName)
                .musclePrincipal(musclePrincipal)
                .musclesSecondaires(musclesSecondaires)
                .typeEquipement(equipement)
                .difficulte(difficulte)
                .build();

        repository.save(def);
        return true;
    }

    private String copyImages(String externalId, JsonNode imagesNode, Path exercicesDir, Path imageSourceDir)
            throws IOException {
        if (!imagesNode.isArray() || imagesNode.size() == 0) return null;
        if (imageSourceDir == null) return null;

        Path destFolder = exercicesDir.resolve(externalId);
        Files.createDirectories(destFolder);

        boolean anycopied = false;
        for (int i = 0; i < Math.min(imagesNode.size(), 2); i++) {
            String relPath = imagesNode.get(i).asText();
            Path src = imageSourceDir.resolve(relPath);
            if (Files.exists(src)) {
                Files.copy(src, destFolder.resolve(i + ".jpg"), StandardCopyOption.REPLACE_EXISTING);
                anycopied = true;
            } else {
                log.debug("Image non trouvée : {}", src);
            }
        }
        return anycopied ? externalId : null;
    }
}
