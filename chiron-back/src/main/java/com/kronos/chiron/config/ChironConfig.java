package com.kronos.chiron.config;

import com.kronos.chiron.ai.AdaptiveTools;
import com.kronos.chiron.ai.AnalyseDieteTools;
import com.kronos.chiron.ai.AppGuideTools;
import com.kronos.chiron.ai.ChironAgent;
import com.kronos.chiron.ai.ChironAgentRouter;
import com.kronos.chiron.ai.FitbitTools;
import com.kronos.chiron.ai.MemoryTools;
import com.kronos.chiron.ai.NutritionTools;
import com.kronos.chiron.ai.RecoveryTools;
import com.kronos.chiron.ai.WorkoutTools;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
                                               ChatMemoryProvider chatMemoryProvider) {

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
                    .maxRetries(2)
                    .logRequestsAndResponses(true)
                    .build();
            agentGemini = AiServices.builder(ChironAgent.class)
                    .chatModel(gemini)
                    .chatMemoryProvider(chatMemoryProvider)
                    .tools(tools)
                    .build();
        }

        return new ChironAgentRouter(agentMistral, agentGemini);
    }

    /**
     * Fournisseur de mémoire à cache partagé : renvoie la même {@link ChatMemory} pour un
     * {@code memoryId} donné, de sorte que les agents Mistral et Gemini conservent un
     * historique commun même quand l'utilisateur bascule de fournisseur.
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        Map<Object, ChatMemory> store = new ConcurrentHashMap<>();
        return memoryId -> store.computeIfAbsent(memoryId,
                id -> MessageWindowChatMemory.withMaxMessages(20));
    }
}
