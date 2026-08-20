package com.kronos.chiron.noctua.agent;

import com.kronos.chiron.coach.agent.AgentRouter;
import com.kronos.chiron.coach.agent.ConversationMemoryManager;

public class NoctuaAgentRouter extends AgentRouter<NoctuaAgent> {

    public NoctuaAgentRouter(NoctuaAgent mistral, NoctuaAgent gemini, ConversationMemoryManager memoryManager) {
        super(mistral, gemini, memoryManager);
    }

    @Override
    protected String call(NoctuaAgent agent, String memoryId, String userMessage) {
        return agent.chat(memoryId, userMessage);
    }
}
