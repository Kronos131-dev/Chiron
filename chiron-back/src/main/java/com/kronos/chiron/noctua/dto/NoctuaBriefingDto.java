package com.kronos.chiron.noctua.dto;

import com.kronos.chiron.noctua.model.NoctuaBriefingType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record NoctuaBriefingDto(
        Long id,
        NoctuaBriefingType type,
        LocalDate dateReference,
        LocalDateTime createdAt,
        boolean lu,
        String premierParagraphe) {
}
