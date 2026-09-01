package com.kronos.chiron.noctua.agent;

import com.kronos.chiron.coach.agent.AgentRouter;
import com.kronos.chiron.coach.agent.AiUnavailableException;
import com.kronos.chiron.coach.agent.ConversationMemoryManager;

public class NoctuaAgentRouter extends AgentRouter<NoctuaAgent> {

    private final boolean actif;

    public NoctuaAgentRouter(NoctuaAgent agent, ConversationMemoryManager memoryManager, boolean actif) {
        super(agent, memoryManager);
        this.actif = actif;
    }

    public boolean actif() {
        return actif;
    }

    // WHY: l'interrupteur est ici plutôt que chez chaque appelant, parce qu'il n'existe pas
    // d'autre chemin vers le modèle depuis Noctua. Coupé, aucun appel ne part — ni pour un
    // briefing programmé, ni pour la réponse à un message.
    @Override
    public String chat(String memoryId, String message) {
        if (!actif) {
            throw new AiUnavailableException("Noctua est en pause.", null);
        }
        return super.chat(memoryId, message);
    }

    @Override
    protected String call(NoctuaAgent agent, String memoryId, String userMessage) {
        return agent.chat(memoryId, userMessage);
    }
}
