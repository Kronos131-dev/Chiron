package com.kronos.chiron.coach.agent;

public class ChironAgentRouter extends AgentRouter<ChironAgent> {

    public ChironAgentRouter(ChironAgent agent, ConversationMemoryManager memoryManager) {
        super(agent, memoryManager);
    }

    @Override
    protected String call(ChironAgent agent, String memoryId, String userMessage) {
        return agent.chat(memoryId, userMessage);
    }
}
