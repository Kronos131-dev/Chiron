package com.kronos.chiron.coach.configuration;

import com.kronos.chiron.coach.tools.AdaptiveTools;
import com.kronos.chiron.coach.tools.AnalyseDieteTools;
import com.kronos.chiron.coach.tools.AppGuideTools;
import com.kronos.chiron.coach.agent.ChironAgent;
import com.kronos.chiron.coach.agent.ChironAgentRouter;
import com.kronos.chiron.coach.agent.ConversationMemoryManager;
import com.kronos.chiron.coach.tools.FitbitTools;
import com.kronos.chiron.coach.tools.MemoryTools;
import com.kronos.chiron.coach.tools.NutritionTools;
import com.kronos.chiron.coach.tools.RecoveryTools;
import com.kronos.chiron.coach.tools.WorkoutTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Configuration de l'intégration IA de Chiron (LangChain4j).
 * Provisionne un agent Mistral et, si une clé Gemini est configurée, un agent Gemini.
 * Les deux partagent les mêmes outils et la même mémoire de conversation ; un routeur
 * ({@link ChironAgentRouter}) choisit l'agent selon la préférence de l'utilisateur.
 */
@Configuration
public class ChironConfig {

    @Value("${langchain4j.mistral-ai.chat-model.api-key}")
    private String mistralApiKey;

    @Value("${langchain4j.mistral-ai.chat-model.model-name}")
    private String mistralModel;

    @Value("${langchain4j.google-ai-gemini.chat-model.api-key:}")
    private String geminiApiKey;

    @Value("${langchain4j.google-ai-gemini.chat-model.model-name:gemini-3.5-flash}")
    private String geminiModel;

    /**
     * Construit le routeur d'agents : un agent Mistral (toujours) et un agent Gemini
     * (seulement si {@code GEMINI_API_KEY} est renseignée). Les deux reçoivent l'ensemble
     * identique d'outils et le même fournisseur de mémoire.
     */
    @Bean
    public ChironAgentRouter chironAgentRouter(WorkoutTools workoutTools,
                                               NutritionTools nutritionTools,
                                               MemoryTools memoryTools,
                                               RecoveryTools recoveryTools,
                                               AdaptiveTools adaptiveTools,
                                               FitbitTools fitbitTools,
                                               AppGuideTools appGuideTools,
                                               AnalyseDieteTools analyseDieteTools,
                                               ChatMemoryProvider chatMemoryProvider,
                                               ConversationMemoryManager memoryManager) {

        Object[] tools = {workoutTools, nutritionTools, memoryTools, recoveryTools,
                adaptiveTools, fitbitTools, appGuideTools, analyseDieteTools};

        ChatModel mistral = MistralAiChatModel.builder()
                .apiKey(mistralApiKey)
                .modelName(mistralModel)
                .timeout(Duration.ofSeconds(90))
                .logRequests(true)
                .logResponses(true)
                .build();
        ChironAgent agentMistral = AiServices.builder(ChironAgent.class)
                .chatModel(mistral)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(tools)
                .build();

        ChironAgent agentGemini = null;
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            ChatModel gemini = GoogleAiGeminiChatModel.builder()
                    .apiKey(geminiApiKey)
                    .modelName(geminiModel)
                    .timeout(Duration.ofSeconds(90))
                    .maxRetries(3)
                    .logRequestsAndResponses(true)
                    .build();
            agentGemini = AiServices.builder(ChironAgent.class)
                    .chatModel(gemini)
                    .chatMemoryProvider(chatMemoryProvider)
                    .tools(tools)
                    .build();
        }

        return new ChironAgentRouter(agentMistral, agentGemini, memoryManager);
    }

    /**
     * Fournisseur de mémoire délégué au {@link ConversationMemoryManager} : la mémoire est
     * indexée par id de conversation et reconstructible depuis la base (cf. le manager).
     * Les agents Mistral et Gemini partagent ainsi la même mémoire pour une conversation donnée.
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider(ConversationMemoryManager memoryManager) {
        return memoryManager::getOrCreate;
    }
}
