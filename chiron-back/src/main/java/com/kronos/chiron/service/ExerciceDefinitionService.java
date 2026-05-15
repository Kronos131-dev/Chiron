package com.kronos.chiron.service;

import com.kronos.chiron.dto.ExerciceDefinitionDto;
import com.kronos.chiron.entity.ExerciceDefinition;
import com.kronos.chiron.entity.MuscleGroup;
import com.kronos.chiron.entity.NiveauDifficulte;
import com.kronos.chiron.entity.TypeEquipement;
import com.kronos.chiron.repository.ExerciceDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ExerciceDefinitionService {

    private final ExerciceDefinitionRepository repository;

    @Transactional(readOnly = true)
    public List<ExerciceDefinitionDto> search(String q, String muscle, String equipement, String difficulte) {
        MuscleGroup muscleEnum = muscle != null ? MuscleGroup.valueOf(muscle) : null;
        TypeEquipement equipementEnum = equipement != null ? TypeEquipement.valueOf(equipement) : null;
        NiveauDifficulte difficulteEnum = difficulte != null ? NiveauDifficulte.valueOf(difficulte) : null;
        // Pattern pré-calculé pour éviter CONCAT dans le JPQL (cause lower(bytea) sur Hibernate 6 + PG)
        String qPattern = (q != null && !q.isBlank()) ? "%" + q.trim().toLowerCase() + "%" : null;

        return repository.search(qPattern, muscleEnum, equipementEnum, difficulteEnum)
                .stream()
                .limit(50)
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExerciceDefinitionDto getById(Long id) {
        return repository.findById(id)
                .map(this::toDto)
                .orElseThrow(() -> new NoSuchElementException("Exercice non trouvé : " + id));
    }

    @Transactional(readOnly = true)
    public Resource streamImage(Long id, int index) {
        if (index != 0 && index != 1) throw new IllegalArgumentException("Index image invalide : " + index);

        if (!repository.existsById(id))
            throw new NoSuchElementException("Exercice non trouvé : " + id);

        byte[] data = index == 0 ? repository.findImage0ById(id) : repository.findImage1ById(id);
        if (data == null || data.length == 0)
            throw new NoSuchElementException("Pas d'image pour l'exercice : " + id + " index " + index);

        return new ByteArrayResource(data);
    }

    // Falls back to relative URL when no request context (unit tests).
    private String buildBaseUrl() {
        try {
            return ServletUriComponentsBuilder.fromCurrentContextPath().toUriString();
        } catch (IllegalStateException e) {
            return "";
        }
    }

    ExerciceDefinitionDto toDto(ExerciceDefinition e) {
        String base = buildBaseUrl();
        String imageUrl = e.getGifPath() != null ? base + "/api/exercices/" + e.getId() + "/image/0" : null;
        String imageUrl2 = e.getGifPath() != null ? base + "/api/exercices/" + e.getId() + "/image/1" : null;
        return new ExerciceDefinitionDto(
                e.getId(),
                e.getNomFr(),
                e.getNomEn(),
                imageUrl,
                imageUrl2,
                e.getMusclePrincipal(),
                e.getMusclesSecondaires(),
                e.getTypeEquipement(),
                e.getDifficulte(),
                e.getDescriptionFr(),
                e.getDescriptionEn()
        );
    }
}
