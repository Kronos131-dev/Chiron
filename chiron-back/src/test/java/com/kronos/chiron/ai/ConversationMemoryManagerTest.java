package com.kronos.chiron.ai;

import com.kronos.chiron.entity.Conversation;
import com.kronos.chiron.entity.ConversationMessage;
import com.kronos.chiron.entity.MessageRole;
import com.kronos.chiron.repository.ConversationMessageRepository;
import com.kronos.chiron.repository.ConversationRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.ChatMemory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationMemoryManagerTest {

    private static final String MEMORY_ID = "42";

    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private ConversationMessageRepository conversationMessageRepository;

    @InjectMocks
    private ConversationMemoryManager memoryManager;

    private static ConversationMessage message(MessageRole role, String content) {
        return ConversationMessage.builder().role(role).content(content).build();
    }

    private void givenPersistedHistory(ConversationMessage... messages) {
        Conversation conversation = Conversation.builder().id(42L).build();
        when(conversationRepository.findById(42L)).thenReturn(Optional.of(conversation));
        when(conversationMessageRepository.findByConversationOrderByCreatedAtAsc(conversation))
                .thenReturn(List.of(messages));
    }

    @Test
    void getOrCreate_firstCall_createsAnEmptyMemory() {
        ChatMemory memory = memoryManager.getOrCreate(MEMORY_ID);

        assertThat(memory).isNotNull();
        assertThat(memory.messages()).isEmpty();
    }

    @Test
    void getOrCreate_sameId_returnsTheSameInstance() {
        assertThat(memoryManager.getOrCreate(MEMORY_ID)).isSameAs(memoryManager.getOrCreate(MEMORY_ID));
    }

    @Test
    void getOrCreate_differentIds_areIsolatedFromEachOther() {
        ChatMemory first = memoryManager.getOrCreate("1");
        first.add(UserMessage.from("bonjour"));

        assertThat(memoryManager.getOrCreate("2").messages()).isEmpty();
    }

    @Test
    void seedIfAbsent_coldMemory_replaysTheHistory() {
        memoryManager.seedIfAbsent(MEMORY_ID, () -> List.of(
                message(MessageRole.USER, "combien de séries ?"),
                message(MessageRole.AI, "quatre")));

        List<ChatMessage> messages = memoryManager.getOrCreate(MEMORY_ID).messages();
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0)).isInstanceOf(UserMessage.class);
        assertThat(messages.get(1)).isInstanceOf(AiMessage.class);
    }

    @Test
    void seedIfAbsent_warmMemory_doesNotTouchTheHistorySupplier() {
        memoryManager.getOrCreate(MEMORY_ID);

        memoryManager.seedIfAbsent(MEMORY_ID, () -> {
            throw new AssertionError("l'historique ne doit pas être relu quand la mémoire est chaude");
        });

        assertThat(memoryManager.getOrCreate(MEMORY_ID).messages()).isEmpty();
    }

    @Test
    void seedIfAbsent_emptyHistory_leavesMemoryEmpty() {
        memoryManager.seedIfAbsent(MEMORY_ID, List::of);

        assertThat(memoryManager.getOrCreate(MEMORY_ID).messages()).isEmpty();
    }

    @Test
    void reset_clearsRamStateAndReplaysFromDatabase() {
        ChatMemory memory = memoryManager.getOrCreate(MEMORY_ID);
        memory.add(UserMessage.from("état corrompu"));
        givenPersistedHistory(message(MessageRole.USER, "question propre"));

        memoryManager.reset(MEMORY_ID);

        List<ChatMessage> messages = memoryManager.getOrCreate(MEMORY_ID).messages();
        assertThat(messages).hasSize(1);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo("question propre");
    }

    @Test
    void reset_replaysUserAndAiMessagesInOrder() {
        givenPersistedHistory(
                message(MessageRole.USER, "u1"),
                message(MessageRole.AI, "a1"),
                message(MessageRole.USER, "u2"));

        memoryManager.reset(MEMORY_ID);

        List<ChatMessage> messages = memoryManager.getOrCreate(MEMORY_ID).messages();
        assertThat(messages).hasSize(3);
        assertThat(((UserMessage) messages.get(0)).singleText()).isEqualTo("u1");
        assertThat(((AiMessage) messages.get(1)).text()).isEqualTo("a1");
        assertThat(((UserMessage) messages.get(2)).singleText()).isEqualTo("u2");
    }

    @Test
    void reset_unknownConversation_leavesMemoryEmpty() {
        ChatMemory memory = memoryManager.getOrCreate(MEMORY_ID);
        memory.add(UserMessage.from("état corrompu"));
        when(conversationRepository.findById(42L)).thenReturn(Optional.empty());

        memoryManager.reset(MEMORY_ID);

        assertThat(memoryManager.getOrCreate(MEMORY_ID).messages()).isEmpty();
        verifyNoInteractions(conversationMessageRepository);
    }

    @Test
    void reset_nonNumericMemoryId_clearsMemoryWithoutHittingTheDatabase() {
        ChatMemory memory = memoryManager.getOrCreate("pas-un-nombre");
        memory.add(UserMessage.from("état corrompu"));

        memoryManager.reset("pas-un-nombre");

        assertThat(memoryManager.getOrCreate("pas-un-nombre").messages()).isEmpty();
        verify(conversationRepository, never()).findById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void evict_removesTheMemorySoTheNextAccessStartsEmpty() {
        ChatMemory memory = memoryManager.getOrCreate(MEMORY_ID);
        memory.add(UserMessage.from("bonjour"));

        memoryManager.evict(MEMORY_ID);

        assertThat(memoryManager.getOrCreate(MEMORY_ID).messages()).isEmpty();
    }

    @Test
    void evict_makesSeedIfAbsentReplayAgain() {
        memoryManager.getOrCreate(MEMORY_ID);
        memoryManager.evict(MEMORY_ID);

        memoryManager.seedIfAbsent(MEMORY_ID, () -> List.of(message(MessageRole.USER, "rechargé")));

        assertThat(memoryManager.getOrCreate(MEMORY_ID).messages()).hasSize(1);
    }

    @Test
    void memory_keepsAtMostTwentyMessages() {
        ChatMemory memory = memoryManager.getOrCreate(MEMORY_ID);
        for (int i = 0; i < 30; i++) {
            memory.add(UserMessage.from("message " + i));
        }

        assertThat(memory.messages()).hasSizeLessThanOrEqualTo(20);
    }
}
