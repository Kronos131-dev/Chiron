package com.kronos.chiron.exportation.dto;

import java.time.LocalDateTime;
import java.util.List;

public record ExportProgrammeDto(
        Long id,
        String nom,
        LocalDateTime creeA,
        List<ModeleExport> modeles,
        List<SeanceExport> seances,
        List<Long> seancesExclues,
        int nbSeances) {
}
