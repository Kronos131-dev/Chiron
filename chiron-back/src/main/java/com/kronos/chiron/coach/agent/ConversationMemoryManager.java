package com.kronos.chiron.coach.agent;

import com.kronos.chiron.coach.model.ConversationMessage;
import com.kronos.chiron.coach.persistence.ConversationMessageRepository;
import com.kronos.chiron.coach.persistence.ConversationRepository;
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

@Component
@RequiredArgsConstructor
public class ConversationMemoryManager {

    private static final Logger log = LoggerFactory.getLogger(ConversationMemoryManager.class);
    private static final int MAX_MESSAGES = 20;

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;

    private final Map<Object, ChatMemory> store = new ConcurrentHashMap<>();

    public ChatMemory getOrCreate(Object memoryId) {
        return store.computeIfAbsent(memoryId, id -> MessageWindowChatMemory.withMaxMessages(MAX_MESSAGES));
    }

    public void seedIfAbsent(String memoryId, Supplier<List<ConversationMessage>> historySupplier) {
        if (store.containsKey(memoryId)) {
            return;
        }
        ChatMemory memory = getOrCreate(memoryId);
        replay(memory, historySupplier.get());
    }

    public void reset(String memoryId) {
        ChatMemory memory = getOrCreate(memoryId);
        memory.clear();
        try {
            Long conversationId = Long.parseLong(memoryId);
            conversationRepository.findById(conversationId).ifPresent(
                    conv -> replay(memory, conversationMessageRepository.findByConversationOrderByCreatedAtAsc(conv)));
        } catch (NumberFormatException e) {
            log.warn("memoryId non numérique '{}', mémoire repartie à vide", memoryId);
        }
    }

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
