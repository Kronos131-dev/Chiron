package com.kronos.chiron.coach.configuration;

import com.kronos.chiron.coach.tools.AdaptiveTools;
import com.kronos.chiron.coach.tools.AnalyseDieteTools;
import com.kronos.chiron.coach.tools.AppGuideTools;
import com.kronos.chiron.coach.agent.ChironAgent;
import com.kronos.chiron.coach.agent.ChironAgentRouter;
import com.kronos.chiron.coach.agent.ConversationMemoryManager;
import com.kronos.chiron.course.agent.CourseVoiceInterpreter;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

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

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ConversationMemoryManager memoryManager) {
        return memoryManager::getOrCreate;
    }

    @Bean
    @ConditionalOnProperty(name = "langchain4j.mistral-ai.chat-model.api-key")
    public CourseVoiceInterpreter courseVoiceInterpreter() {
        ChatModel mistral = MistralAiChatModel.builder()
                .apiKey(mistralApiKey)
                .modelName(mistralModel)
                .timeout(Duration.ofSeconds(3))
                .logRequests(true)
                .logResponses(true)
                .build();

        return AiServices.builder(CourseVoiceInterpreter.class)
                .chatModel(mistral)
                .build();
    }
}
