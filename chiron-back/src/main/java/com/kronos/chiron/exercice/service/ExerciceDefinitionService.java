package com.kronos.chiron.exercice.service;

import com.kronos.chiron.exercice.dto.ExerciceDefinitionDto;
import org.springframework.core.io.Resource;

import java.util.List;

public interface ExerciceDefinitionService {

    List<ExerciceDefinitionDto> search(String q, String muscle, String equipement, String difficulte);

    ExerciceDefinitionDto getById(Long id);

    Resource streamImage(Long id, int index);
}
