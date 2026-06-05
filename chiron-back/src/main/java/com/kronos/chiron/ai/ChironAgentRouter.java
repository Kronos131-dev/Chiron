package com.kronos.chiron.ai;

import com.kronos.chiron.entity.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Aiguille le chat vers l'agent Mistral ou Gemini selon la préférence de l'utilisateur.
 * L'agent Gemini peut être absent (clé non configurée) : on retombe alors sur Mistral.
 * Les deux agents partagent la même mémoire de conversation (cf. ChironConfig).
 */
public class ChironAgentRouter {

    private static final Logger log = LoggerFactory.getLogger(ChironAgentRouter.class);

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

    /**
     * Exécute le chat sur l'agent du fournisseur demandé, avec repli sur Mistral si l'agent
     * choisi échoue à l'exécution (clé invalide, quota, schéma d'outils rejeté par Gemini…).
     * Évite ainsi qu'une panne Gemini ne se traduise par un 500 côté utilisateur.
     */
    public String chatWithFallback(AiProvider provider, String memoryId, String message) {
        ChironAgent agent = forProvider(provider);
        try {
            return agent.chat(memoryId, message);
        } catch (RuntimeException e) {
            if (agent != mistral) {
                log.warn("Agent {} en échec, repli sur Mistral", provider, e);
                return mistral.chat(memoryId, message);
            }
            throw e;
        }
    }

    public boolean geminiAvailable() {
        return gemini != null;
    }
}
