package com.kronos.chiron.noctua.agent;

import com.kronos.chiron.coach.agent.AgentRouter;
import com.kronos.chiron.coach.agent.ConversationMemoryManager;

public class NoctuaAgentRouter extends AgentRouter<NoctuaAgent> {

    public NoctuaAgentRouter(NoctuaAgent agent, ConversationMemoryManager memoryManager) {
        super(agent, memoryManager);
    }

    @Override
    protected String call(NoctuaAgent agent, String memoryId, String userMessage) {
        return agent.chat(memoryId, userMessage);
    }
}
