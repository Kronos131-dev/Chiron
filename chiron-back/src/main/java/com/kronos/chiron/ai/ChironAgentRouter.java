package com.kronos.chiron.ai;

import com.kronos.chiron.entity.AiProvider;

/**
 * Aiguille le chat vers l'agent Mistral ou Gemini selon la préférence de l'utilisateur.
 * L'agent Gemini peut être absent (clé non configurée) : on retombe alors sur Mistral.
 * Les deux agents partagent la même mémoire de conversation (cf. ChironConfig).
 */
public class ChironAgentRouter {

    private final ChironAgent mistral;
    private final ChironAgent gemini; // nullable si GEMINI_API_KEY absente

    public ChironAgentRouter(ChironAgent mistral, ChironAgent gemini) {
        this.mistral = mistral;
        this.gemini = gemini;
    }

    /** Agent correspondant au fournisseur demandé, avec repli sur Mistral si Gemini indisponible. */
    public ChironAgent forProvider(AiProvider provider) {
        if (provider == AiProvider.GEMINI && gemini != null) {
            return gemini;
        }
        return mistral;
    }

    public boolean geminiAvailable() {
        return gemini != null;
    }
}
