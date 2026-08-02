package com.kronos.chiron.coach.dto;

/**
 * Réponse du coach IA, accompagnée de l'id de la conversation (utile au front pour rattacher
 * les messages suivants, notamment lors de la création d'une nouvelle conversation).
 */
public record ChatResponse(Long conversationId, String reply) {}
