package com.kronos.chiron.exportation.service;

import com.kronos.chiron.exportation.dto.ExportProgrammeDto;
import com.kronos.chiron.exportation.dto.ExportProgrammeRequest;
import com.kronos.chiron.exportation.dto.FichierExporte;

import java.util.List;

public interface ExportProgrammeService {

    List<ExportProgrammeDto> lister(String username);

    ExportProgrammeDto detail(String username, Long id);

    ExportProgrammeDto creer(String username, ExportProgrammeRequest requete);

    ExportProgrammeDto modifier(String username, Long id, ExportProgrammeRequest requete);

    void supprimer(String username, Long id);

    FichierExporte telecharger(String username, Long id);
}
