package com.kronos.chiron.coach.agent;

import com.kronos.chiron.utilisateur.model.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public class ChironAgentRouter {

    private static final Logger log = LoggerFactory.getLogger(ChironAgentRouter.class);

    private static final int MAX_ATTEMPTS = 2;
    private static final long BASE_BACKOFF_MS = 400L;

    private final ChironAgent mistral;
    private final ChironAgent gemini;
    private final ConversationMemoryManager memoryManager;

    public ChironAgentRouter(ChironAgent mistral, ChironAgent gemini, ConversationMemoryManager memoryManager) {
        this.mistral = mistral;
        this.gemini = gemini;
        this.memoryManager = memoryManager;
    }

    public ChironAgent forProvider(AiProvider provider) {
        if (provider == AiProvider.GEMINI && gemini != null) {
            return gemini;
        }
        return mistral;
    }

    public String chatWithFallback(AiProvider provider, String memoryId, String message) {
        ChironAgent agent = forProvider(provider);
        RuntimeException last = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return agent.chat(memoryId, message);
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

        // WHY: repli sur Mistral seulement si l'agent en échec n'était pas déjà Mistral.
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
