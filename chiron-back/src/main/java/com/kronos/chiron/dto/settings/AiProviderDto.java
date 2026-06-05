package com.kronos.chiron.dto.settings;

import com.kronos.chiron.entity.AiProvider;

/**
 * Fournisseur d'IA choisi par l'utilisateur pour le coach Chiron.
 *
 * @param provider        MISTRAL ou GEMINI
 * @param geminiAvailable true si l'agent Gemini est réellement provisionné côté serveur
 *                        (clé configurée). Sinon, choisir Gemini retombe sur Mistral.
 *                        Ignoré dans le corps des requêtes PUT.
 */
public record AiProviderDto(AiProvider provider, boolean geminiAvailable) {}
