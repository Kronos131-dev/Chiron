package com.kronos.chiron.config;

import com.kronos.chiron.ai.ChironAgent;
import com.kronos.chiron.ai.NutritionTools;
import com.kronos.chiron.ai.WorkoutTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration class for the Chiron AI integration.
 * It provisions the necessary beans for integrating with Mistral AI using LangChain4j,
 * including the chat model, memory provider, and the AI agent itself.
 */
@Configuration
public class ChironConfig {

    @Value("${langchain4j.mistral-ai.chat-model.api-key}")
    private String mistralApiKey;

    @Value("${langchain4j.mistral-ai.chat-model.model-name}")
    private String modelName;

    /**
     * Creates and configures the ChironAgent bean.
     * This agent serves as the primary interface for AI interactions, tying together
     * the language model, contextual memory, and available domain-specific tools.
     *
     * @param chatModel          The configured chat model to handle interactions.
     * @param workoutTools       The tools available to the AI for workout-related queries.
     * @param chatMemoryProvider The provider handling memory context for conversations.
     * @return A fully constructed ChironAgent instance.
     */
    @Bean
    public ChironAgent chironAgent(ChatModel chatModel,
                                   WorkoutTools workoutTools,
                                   NutritionTools nutritionTools,
                                   ChatMemoryProvider chatMemoryProvider) {
        return AiServices.builder(ChironAgent.class)
                .chatModel(chatModel)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(workoutTools, nutritionTools)
                .build();
    }

    /**
     * Provides a memory provider for the chat interactions.
     * Ensures that each user session retains a specific window of previous messages
     * to maintain conversation context.
     *
     * @return A ChatMemoryProvider instance configured with a message window.
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return userId -> MessageWindowChatMemory.withMaxMessages(20);
    }

    /**
     * Configures the primary Mistral AI ChatModel bean.
     * Establishes the connection using the provided API key and specifies logging behavior.
     *
     * @return A configured ChatModel instance for interacting with the Mistral API.
     */
    @Bean
    public ChatModel chatModel() {
        return MistralAiChatModel.builder()
                .apiKey(mistralApiKey)
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
