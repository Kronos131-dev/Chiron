package com.kronos.chiron.coach.dto;

import java.time.LocalDateTime;

/** Résumé d'une conversation pour l'historique (menu de la page Chat). */
public record ConversationSummaryDto(Long id, String titre, LocalDateTime updatedAt) {}
