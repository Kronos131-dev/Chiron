package com.kronos.chiron.coach.configuration;

import com.kronos.chiron.coach.agent.ChironAgent;
import com.kronos.chiron.coach.agent.ChironAgentRouter;
import com.kronos.chiron.coach.agent.ConversationMemoryManager;
import com.kronos.chiron.coach.tools.AdaptiveTools;
import com.kronos.chiron.coach.tools.AnalyseDieteTools;
import com.kronos.chiron.coach.tools.AppGuideTools;
import com.kronos.chiron.coach.tools.FitbitTools;
import com.kronos.chiron.coach.tools.MemoryTools;
import com.kronos.chiron.coach.tools.NutritionTools;
import com.kronos.chiron.coach.tools.RecoveryTools;
import com.kronos.chiron.coach.tools.WorkoutTools;
import com.kronos.chiron.course.agent.CourseVoiceInterpreter;
import com.kronos.chiron.course.dto.CommandeVoixDto;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChironConfig {

    private final ModeleIaConfig modeleIaConfig;

    public ChironConfig(ModeleIaConfig modeleIaConfig) {
        this.modeleIaConfig = modeleIaConfig;
    }

    @Bean
    public ChironAgentRouter chironAgentRouter(@Qualifier(ModeleIaConfig.CONVERSATION) ChatModel modele,
            WorkoutTools workoutTools,
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

        ChironAgent agent = AiServices.builder(ChironAgent.class)
                .chatModel(modele)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(tools)
                .build();

        return new ChironAgentRouter(agent, memoryManager);
    }

    @Bean
    public ChatMemoryProvider chatMemoryProvider(ConversationMemoryManager memoryManager) {
        return memoryManager::getOrCreate;
    }

    // WHY: sans clé, l'interprète rend « inconnu » plutôt que d'échouer. Le service Android
    // interroge cette route pour chaque phrase qu'il n'a pas su lire lui-même ; une exception y
    // coûterait une seconde de course et n'apprendrait rien de plus qu'un ordre non reconnu.
    @Bean
    public CourseVoiceInterpreter courseVoiceInterpreter(@Qualifier(ModeleIaConfig.VOIX) ChatModel modele) {
        if (!modeleIaConfig.configure()) {
            return transcript -> new CommandeVoixDto("inconnu", null);
        }
        return AiServices.builder(CourseVoiceInterpreter.class)
                .chatModel(modele)
                .build();
    }
}
