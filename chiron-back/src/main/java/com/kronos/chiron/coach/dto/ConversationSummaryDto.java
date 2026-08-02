package com.kronos.chiron.coach.dto;

import java.time.LocalDateTime;

public record ConversationSummaryDto(Long id, String titre, LocalDateTime updatedAt) {
}
