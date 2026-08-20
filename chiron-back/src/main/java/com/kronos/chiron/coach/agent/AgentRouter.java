package com.kronos.chiron.coach.agent;

import com.kronos.chiron.utilisateur.model.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public abstract class AgentRouter<A> {

    private static final Logger log = LoggerFactory.getLogger(AgentRouter.class);

    private static final int MAX_ATTEMPTS = 2;
    private static final long BASE_BACKOFF_MS = 400L;

    private final A mistral;
    private final A gemini;
    private final ConversationMemoryManager memoryManager;

    protected AgentRouter(A mistral, A gemini, ConversationMemoryManager memoryManager) {
        this.mistral = mistral;
        this.gemini = gemini;
        this.memoryManager = memoryManager;
    }

    protected abstract String call(A agent, String memoryId, String userMessage);

    public A forProvider(AiProvider provider) {
        if (provider == AiProvider.GEMINI && gemini != null) {
            return gemini;
        }
        return mistral;
    }

    public String chatWithFallback(AiProvider provider, String memoryId, String message) {
        A agent = forProvider(provider);

        try {
            return chatWithRetries(agent, provider, memoryId, message);
        } catch (RuntimeException primaryFailure) {
            // WHY: repli sur Mistral seulement si l'agent en échec n'était pas déjà Mistral.
            if (agent == mistral) {
                throw unavailable(provider, primaryFailure);
            }
            log.warn("Agent {} en échec, repli sur Mistral", provider, primaryFailure);
            memoryManager.reset(memoryId);
            try {
                return chatWithRetries(mistral, AiProvider.MISTRAL, memoryId, message);
            } catch (RuntimeException fallbackFailure) {
                throw unavailable(provider, fallbackFailure);
            }
        }
    }

    private String chatWithRetries(A agent, AiProvider provider, String memoryId, String message) {
        RuntimeException last = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return call(agent, memoryId, message);
            } catch (RuntimeException e) {
                last = e;
                if (attempt < MAX_ATTEMPTS && isTransient(e)) {
                    log.warn("Agent {} : erreur transitoire (tentative {}/{}), réessai", provider, attempt,
                            MAX_ATTEMPTS, e);
                    memoryManager.reset(memoryId);
                    sleep(BASE_BACKOFF_MS * attempt);
                } else {
                    break;
                }
            }
        }

        throw last;
    }

    private AiUnavailableException unavailable(AiProvider provider, RuntimeException cause) {
        log.error("Aucun agent n'a répondu : fournisseur demandé {}, jusqu'à {} tentatives par agent,"
                + " repli Mistral {}", provider, MAX_ATTEMPTS,
                provider == AiProvider.MISTRAL || gemini == null ? "sans objet" : "épuisé", cause);
        return new AiUnavailableException("Le coach IA est temporairement indisponible.", cause);
    }

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
