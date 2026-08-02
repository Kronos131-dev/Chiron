package com.kronos.chiron.programme.service;

import com.kronos.chiron.seance.dto.SeanceDto;
import com.kronos.chiron.seance.model.Seance;

import java.util.List;

public interface ProgrammeService {

    Seance sauvegarderProgramme(String username, SeanceDto seanceDto);

    Seance sauvegarderProgramme(String username, SeanceDto seanceDto, String forUsername);

    List<Seance> getProgrammes(String username);

    void reorderProgrammes(String username, List<Long> orderedIds);

    Seance getProgrammeById(Long id, String username);

    void deleteProgramme(Long id, String username);

    Seance copyProgramme(Long programmeId, String targetUsername);
}
