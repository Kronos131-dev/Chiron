package com.kronos.chiron.coach.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

public abstract class AgentRouter<A> {

    private static final Logger log = LoggerFactory.getLogger(AgentRouter.class);

    private static final int MAX_ATTEMPTS = 2;
    private static final long BASE_BACKOFF_MS = 400L;

    private final A agent;
    private final ConversationMemoryManager memoryManager;

    protected AgentRouter(A agent, ConversationMemoryManager memoryManager) {
        this.agent = agent;
        this.memoryManager = memoryManager;
    }

    protected abstract String call(A agent, String memoryId, String userMessage);

    // WHY: un seul modèle depuis le passage à OpenRouter, donc plus de repli d'un fournisseur
    // sur l'autre — il ne reste que la relance, qui traite ce qu'elle a toujours traité : le 429
    // et le 503 d'un service occupé. La mémoire est réinitialisée entre deux essais parce qu'un
    // appel d'outil interrompu y laisse un tour à moitié écrit, que le modèle refuse ensuite.
    public String chat(String memoryId, String message) {
        RuntimeException derniere = null;

        for (int tentative = 1; tentative <= MAX_ATTEMPTS; tentative++) {
            try {
                return call(agent, memoryId, message);
            } catch (RuntimeException e) {
                derniere = e;
                if (tentative < MAX_ATTEMPTS && isTransient(e)) {
                    log.warn("Coach IA : erreur transitoire (tentative {}/{}), réessai", tentative,
                            MAX_ATTEMPTS, e);
                    memoryManager.reset(memoryId);
                    sleep(BASE_BACKOFF_MS * tentative);
                } else {
                    break;
                }
            }
        }

        log.error("Le coach IA n'a pas répondu après {} tentatives", MAX_ATTEMPTS, derniere);
        throw new AiUnavailableException("Le coach IA est temporairement indisponible.", derniere);
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
}
