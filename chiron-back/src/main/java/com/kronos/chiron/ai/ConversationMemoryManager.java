package com.kronos.chiron.ai;

import com.kronos.chiron.entity.Conversation;
import com.kronos.chiron.entity.ConversationMessage;
import com.kronos.chiron.repository.ConversationMessageRepository;
import com.kronos.chiron.repository.ConversationRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Gère la mémoire de conversation de l'IA, indexée par id de conversation (et non par
 * utilisateur). La mémoire vit en RAM mais peut être reconstruite à tout moment depuis les
 * messages persistés : à froid (redémarrage / rechargement d'une vieille conversation) ou
 * après un échec d'appel modèle (pour repartir d'un état propre, sans requête d'outil orpheline).
 *
 * <p>Le replay ne réinjecte que le texte user/IA (pas les appels d'outils), ce qui garantit une
 * mémoire toujours valide et allégée. Remplace l'ancien {@code ChatMemoryProvider} en RAM pure.
 */
@Component
@RequiredArgsConstructor
public class ConversationMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryManager.class);
    private static final int MAX_MESSAGES = 20;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;

    private final Map<Object, ChatMemory> store = new ConcurrentHashMap<>();

    /** Renvoie (ou crée à vide) la mémoire d'une conversation. Utilisé par le ChatMemoryProvider. */
    public ChatMemory getOrCreate(Object memoryId) {
        return store.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES));
    }

    /**
     * Si la mémoire de cette conversation n'est pas encore chargée (ex. après redémarrage ou
     * rechargement d'une ancienne conversation), la reconstruit depuis l'historique fourni.
     * Ne fait rien si la mémoire est déjà chaude — on évite alors de toucher la base.
     */
    public void seedIfAbsent(String memoryId, Supplier<List<ConversationMessage>> historySupplier) {
        if (store.containsKey(memoryId)) {
            return;
        }
        ChatMemory memory = getOrCreate(memoryId);
        replay(memory, historySupplier.get());
    }

    /**
     * Réinitialise la mémoire d'une conversation à partir des messages persistés : purge l'état
     * RAM (potentiellement corrompu après un échec modèle) et rejoue l'historique propre depuis
     * la base. À appeler avant un réessai ou un repli sur un autre fournisseur.
     */
    public void reset(String memoryId) {
        ChatMemory memory = getOrCreate(memoryId);
        memory.clear();
        try {
            Long conversationId = Long.parseLong(memoryId);
            conversationRepository.findById(conversationId).ifPresent(conv ->
                    replay(memory, conversationMessageRepository.findByConversationOrderByCreatedAtAsc(conv)));
        } catch (NumberFormatException e) {
            log.warn("memoryId non numérique '{}', mémoire repartie à vide", memoryId);
        }
    }

    /** Supprime la mémoire d'une conversation (ex. à la suppression de la conversation). */
    public void evict(String memoryId) {
        store.remove(memoryId);
    }

    private void replay(ChatMemory memory, List<ConversationMessage> history) {
        for (ConversationMessage m : history) {
            switch (m.getRole()) {
                case USER -> memory.add(UserMessage.from(m.getContent()));
                case AI -> memory.add(AiMessage.from(m.getContent()));
            }
        }
    }
}
