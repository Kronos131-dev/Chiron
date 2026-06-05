package com.kronos.chiron.dto.settings;

import com.kronos.chiron.entity.AiProvider;

/**
 * Fournisseur d'IA choisi par l'utilisateur pour le coach Chiron.
 *
 * @param provider MISTRAL ou GEMINI
 */
public record AiProviderDto(AiProvider provider) {}
