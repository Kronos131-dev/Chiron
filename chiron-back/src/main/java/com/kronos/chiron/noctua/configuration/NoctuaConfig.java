package com.kronos.chiron.noctua.configuration;

import com.kronos.chiron.coach.agent.ConversationMemoryManager;
import com.kronos.chiron.coach.tools.MemoryTools;
import com.kronos.chiron.coach.tools.RecoveryTools;
import com.kronos.chiron.noctua.agent.NoctuaAgent;
import com.kronos.chiron.noctua.agent.NoctuaAgentRouter;
import com.kronos.chiron.noctua.tools.SanteTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class NoctuaConfig {

    @Value("${langchain4j.mistral-ai.chat-model.api-key}")
    private String mistralApiKey;

    @Value("${langchain4j.mistral-ai.chat-model.model-name}")
    private String mistralModel;

    @Value("${langchain4j.google-ai-gemini.chat-model.api-key:}")
    private String geminiApiKey;

    @Value("${langchain4j.google-ai-gemini.chat-model.model-name:gemini-3.5-flash}")
    private String geminiModel;

    @Bean
    public NoctuaAgentRouter noctuaAgentRouter(SanteTools santeTools,
            MemoryTools memoryTools,
            RecoveryTools recoveryTools,
            ChatMemoryProvider chatMemoryProvider,
            ConversationMemoryManager memoryManager) {

        Object[] tools = {santeTools, memoryTools, recoveryTools};

        ChatModel mistral = MistralAiChatModel.builder()
                .apiKey(mistralApiKey)
                .modelName(mistralModel)
                .timeout(Duration.ofSeconds(90))
                .logRequests(true)
                .logResponses(true)
                .build();
        NoctuaAgent agentMistral = AiServices.builder(NoctuaAgent.class)
                .chatModel(mistral)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(tools)
                .build();

        NoctuaAgent agentGemini = null;
        if (geminiApiKey != null && !geminiApiKey.isBlank()) {
            ChatModel gemini = GoogleAiGeminiChatModel.builder()
                    .apiKey(geminiApiKey)
                    .modelName(geminiModel)
                    .timeout(Duration.ofSeconds(90))
                    .maxRetries(3)
                    .logRequestsAndResponses(true)
                    .build();
            agentGemini = AiServices.builder(NoctuaAgent.class)
                    .chatModel(gemini)
                    .chatMemoryProvider(chatMemoryProvider)
                    .tools(tools)
                    .build();
        }

        return new NoctuaAgentRouter(agentMistral, agentGemini, memoryManager);
    }
}
