package com.kronos.chiron.noctua.configuration;

import com.kronos.chiron.coach.agent.ConversationMemoryManager;
import com.kronos.chiron.coach.configuration.ModeleIaConfig;
import com.kronos.chiron.coach.tools.MemoryTools;
import com.kronos.chiron.coach.tools.RecoveryTools;
import com.kronos.chiron.noctua.agent.NoctuaAgent;
import com.kronos.chiron.noctua.agent.NoctuaAgentRouter;
import com.kronos.chiron.noctua.tools.SanteTools;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NoctuaConfig {

    @Value("${chiron.noctua.ia-active:true}")
    private boolean iaActive;

    @Bean
    public NoctuaAgentRouter noctuaAgentRouter(@Qualifier(ModeleIaConfig.CONVERSATION) ChatModel modele,
            SanteTools santeTools,
            MemoryTools memoryTools,
            RecoveryTools recoveryTools,
            ChatMemoryProvider chatMemoryProvider,
            ConversationMemoryManager memoryManager) {

        Object[] tools = {santeTools, memoryTools, recoveryTools};

        NoctuaAgent agent = AiServices.builder(NoctuaAgent.class)
                .chatModel(modele)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(tools)
                .build();

        return new NoctuaAgentRouter(agent, memoryManager, iaActive);
    }
}
