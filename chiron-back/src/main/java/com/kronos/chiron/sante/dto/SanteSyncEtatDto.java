package com.kronos.chiron.sante.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record SanteSyncEtatDto(
        String typeDonnee,
        LocalDate derniereDateSynchronisee,
        LocalDateTime derniereExecution,
        String statut,
        String message) {
}
