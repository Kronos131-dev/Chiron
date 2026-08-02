package com.kronos.chiron.ai;

import com.kronos.chiron.utilisateur.model.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * Aiguille le chat vers l'agent Mistral ou Gemini selon la préférence de l'utilisateur, avec une
 * stratégie de résilience : réessais avec backoff court sur erreurs transitoires (Gemini répond
 * souvent {@code 503 UNAVAILABLE} / « overloaded »), puis repli sur Mistral. Avant chaque réessai
 * ou repli, la mémoire de la conversation est réinitialisée depuis la base pour éviter qu'un appel
 * interrompu laisse une requête d'outil orpheline qui ferait échouer les tentatives suivantes.
 */
public class ChironAgentRouter {

    private static final Logger log = LoggerFactory.getLogger(ChironAgentRouter.class);

    /** Nombre de tentatives sur l'agent demandé avant de basculer sur le repli. */
    private static final int MAX_ATTEMPTS = 2;
    /** Backoff de base entre deux tentatives (doublé à chaque essai). */
    private static final long BASE_BACKOFF_MS = 400L;

    private final ChironAgent mistral;
    private final ChironAgent gemini; // nullable si GEMINI_API_KEY absente
    private final ConversationMemoryManager memoryManager;

    public ChironAgentRouter(ChironAgent mistral, ChironAgent gemini, ConversationMemoryManager memoryManager) {
        this.mistral = mistral;
        this.gemini = gemini;
        this.memoryManager = memoryManager;
    }

    /** Agent correspondant au fournisseur demandé, avec repli sur Mistral si Gemini indisponible. */
    public ChironAgent forProvider(AiProvider provider) {
        if (provider == AiProvider.GEMINI && gemini != null) {
            return gemini;
        }
        return mistral;
    }

    /**
     * Exécute le chat avec réessais sur erreurs transitoires puis repli sur Mistral. Ne propage
     * une {@link AiUnavailableException} que si tout a échoué.
     */
    public String chatWithFallback(AiProvider provider, String memoryId, String message) {
        ChironAgent agent = forProvider(provider);
        RuntimeException last = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return agent.chat(memoryId, message);
            } catch (RuntimeException e) {
                last = e;
                if (attempt < MAX_ATTEMPTS && isTransient(e)) {
                    log.warn("Agent {} : erreur transitoire (tentative {}/{}), réessai", provider, attempt, MAX_ATTEMPTS, e);
                    memoryManager.reset(memoryId); // repart d'un état propre
                    sleep(BASE_BACKOFF_MS * attempt);
                } else {
                    break;
                }
            }
        }

        // Repli sur Mistral si l'agent en échec n'était pas déjà Mistral.
        if (agent != mistral) {
            log.warn("Agent {} en échec, repli sur Mistral", provider, last);
            memoryManager.reset(memoryId);
            try {
                return mistral.chat(memoryId, message);
            } catch (RuntimeException e) {
                last = e;
            }
        }

        throw new AiUnavailableException("Le coach IA est temporairement indisponible.", last);
    }

    /** Erreur jugée transitoire (surcharge, indisponibilité, timeout) → mérite un réessai. */
    private boolean isTransient(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase(Locale.ROOT);
                if (m.contains("503") || m.contains("unavailable") || m.contains("overloaded")
                        || m.contains("timeout") || m.contains("timed out") || m.contains("deadline")
                        || m.contains("429") || m.contains("rate limit")) {
                    return true;
                }
            }
        }
        return false;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean geminiAvailable() {
        return gemini != null;
    }
}
