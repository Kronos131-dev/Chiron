package com.kronos.chiron.noctua.dto;

import com.kronos.chiron.coach.dto.ConversationMessageDto;

import java.util.List;

public record NoctuaBriefingDetailDto(NoctuaBriefingDto briefing, List<ConversationMessageDto> messages) {
}
