package com.kronos.chiron.coach.configuration;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.Map;

@Configuration
public class ModeleIaConfig {

    public static final String CONVERSATION = "modeleConversation";
    public static final String VOIX = "modeleVoix";

    // WHY: le coach a le temps de réfléchir, l'interprète de commandes vocales n'en a aucun —
    // l'athlète court, l'ordre est dit, et une réponse qui arrive après trois secondes arrive
    // après que le service a renoncé.
    private static final Duration DELAI_CONVERSATION = Duration.ofSeconds(90);
    private static final Duration DELAI_VOIX = Duration.ofSeconds(3);

    @Value("${langchain4j.open-ai.chat-model.api-key:}")
    private String cleApi;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modele;

    public boolean configure() {
        return cleApi != null && !cleApi.isBlank();
    }

    @Bean(CONVERSATION)
    public ChatModel modeleConversation() {
        return construire(DELAI_CONVERSATION);
    }

    @Bean(VOIX)
    public ChatModel modeleVoix() {
        return construire(DELAI_VOIX);
    }

    // WHY: une seule tentative ici. AgentRouter relance déjà, en réinitialisant la mémoire de
    // conversation entre deux essais ; une relance interne au client rejouerait le même appel
    // avec un historique que le premier échec a peut-être laissé à moitié écrit.
    private ChatModel construire(Duration delai) {
        return OpenAiChatModel.builder()
                .apiKey(cleApi)
                .baseUrl(baseUrl)
                .modelName(modele)
                .timeout(delai)
                .maxRetries(1)
                .customHeaders(Map.of("X-Title", "Chiron"))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}
