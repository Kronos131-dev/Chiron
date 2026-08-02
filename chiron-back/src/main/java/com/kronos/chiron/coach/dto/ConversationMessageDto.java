package com.kronos.chiron.coach.dto;

/** Un message d'une conversation, tel qu'affiché lors du rechargement (role = USER | AI). */
public record ConversationMessageDto(String role, String content) {}
