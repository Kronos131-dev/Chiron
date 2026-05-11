package com.kronos.chiron.service;

import com.kronos.chiron.ai.ChironAgent;
import com.kronos.chiron.ai.WorkoutTools;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service responsible for managing the AI coach (Chiron) integration.
 * Initializes the Mistral AI model, manages conversational memory per user,
 * and acts as the bridge between the controller layer and the LangChain4j agent.
 */
@Service
public class ChironService {

    private final ChironAgent agent;
    private final Map<Object, ChatMemory> memories = new ConcurrentHashMap<>();

    /**
     * Constructs the ChironService, configuring the chat model, memory provider, and AI agent.
     *
     * @param workoutTools The suite of tools available to the AI.
     * @param apiKey       The API key for the Mistral AI model, injected from configuration.
     */
    public ChironService(WorkoutTools workoutTools,
                         @Value("${langchain4j.mistral-ai.chat-model.api-key}") String apiKey) {

        ChatModel chatModel = MistralAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("mistral-small-latest")
                .temperature(0.3)
                .timeout(Duration.ofMinutes(2))
                .build();

        ChatMemoryProvider chatMemoryProvider = memoryId -> memories.computeIfAbsent(memoryId, id ->
                MessageWindowChatMemory.withMaxMessages(100)
        );

        this.agent = AiServices.builder(ChironAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(workoutTools)
                .build();
    }

    /**
     * Processes a user's message by sending it to the AI agent.
     *
     * @param userId      The ID of the user, used as the memory context identifier.
     * @param userMessage The message input provided by the user.
     * @return The AI's generated response.
     */
    public String talkToCoach(Long userId, String userMessage) {
        return agent.chat(userId.toString(), userMessage);
    }

    /**
     * Clears the conversational memory for a specific hardcoded test user session.
     * This ensures previous context does not pollute new workout sessions.
     */
    public void endSession() {
        ChatMemory memory = memories.get("user-1");
        if (memory != null) {
            memory.clear();
        }
    }
}
