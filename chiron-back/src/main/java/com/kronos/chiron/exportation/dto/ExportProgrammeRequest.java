package com.kronos.chiron.exportation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExportProgrammeRequest(
        @NotBlank @Size(max = 120) String nom,
        @NotEmpty List<Long> modeleIds,
        List<SeanceExport> seances,
        List<Long> seancesExclues) {
}
