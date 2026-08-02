package com.kronos.chiron.utilisateur.dto;

import com.kronos.chiron.utilisateur.model.AiProvider;

public record AiProviderDto(AiProvider provider, boolean geminiAvailable) {
}
