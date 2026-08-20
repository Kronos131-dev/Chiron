package com.kronos.chiron.coach.agent;

public class ChironAgentRouter extends AgentRouter<ChironAgent> {

    public ChironAgentRouter(ChironAgent mistral, ChironAgent gemini, ConversationMemoryManager memoryManager) {
        super(mistral, gemini, memoryManager);
    }

    @Override
    protected String call(ChironAgent agent, String memoryId, String userMessage) {
        return agent.chat(memoryId, userMessage);
    }
}
