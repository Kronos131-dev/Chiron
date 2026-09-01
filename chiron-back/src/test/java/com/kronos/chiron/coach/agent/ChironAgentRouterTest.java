package com.kronos.chiron.coach.agent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChironAgentRouterTest {

    private static final String MEMORY_ID = "conv-1";
    private static final String MESSAGE = "combien de séries ?";

    @Mock
    private ChironAgent agent;
    @Mock
    private ConversationMemoryManager memoryManager;

    private ChironAgentRouter router() {
        return new ChironAgentRouter(agent, memoryManager);
    }

    private static RuntimeException transientError(String message) {
        return new RuntimeException(message);
    }

    @Test
    void chat_firstAttemptSucceeds_returnsReplyWithoutTouchingMemory() {
        when(agent.chat(MEMORY_ID, MESSAGE)).thenReturn("quatre");

        String reply = router().chat(MEMORY_ID, MESSAGE);

        assertThat(reply).isEqualTo("quatre");
        verify(agent).chat(MEMORY_ID, MESSAGE);
        verifyNoInteractions(memoryManager);
    }

    // WHY: la mémoire est réinitialisée entre deux essais parce qu'un appel d'outil interrompu y
    // laisse un tour à moitié écrit, que le modèle refuse ensuite — l'erreur suivante ne serait
    // plus celle qu'on réessayait.
    @Test
    void chat_transientErrorThenSuccess_retriesAfterResettingMemory() {
        when(agent.chat(MEMORY_ID, MESSAGE))
                .thenThrow(transientError("503 UNAVAILABLE"))
                .thenReturn("quatre");

        String reply = router().chat(MEMORY_ID, MESSAGE);

        assertThat(reply).isEqualTo("quatre");
        verify(agent, times(2)).chat(MEMORY_ID, MESSAGE);
        verify(memoryManager).reset(MEMORY_ID);
    }

    @Test
    void chat_transientErrorTwice_throwsAiUnavailableCarryingTheCause() {
        RuntimeException panne = transientError("model is overloaded");
        when(agent.chat(MEMORY_ID, MESSAGE)).thenThrow(panne);

        assertThatThrownBy(() -> router().chat(MEMORY_ID, MESSAGE))
                .isInstanceOf(AiUnavailableException.class)
                .hasCause(panne);

        verify(agent, times(2)).chat(MEMORY_ID, MESSAGE);
    }

    // WHY: une clé invalide ou une requête malformée ne guérit pas d'une seconde tentative. Ne
    // relancer que le transitoire est ce qui distingue une seconde d'attente d'une seconde
    // perdue à coup sûr.
    @Test
    void chat_definitiveError_isNotRetried() {
        when(agent.chat(MEMORY_ID, MESSAGE)).thenThrow(new RuntimeException("400 bad request"));

        assertThatThrownBy(() -> router().chat(MEMORY_ID, MESSAGE))
                .isInstanceOf(AiUnavailableException.class);

        verify(agent, times(1)).chat(MEMORY_ID, MESSAGE);
    }

    @Test
    void chat_transientKeywordNestedInCause_isStillRetried() {
        RuntimeException nested = new RuntimeException("call failed",
                new IllegalStateException("upstream returned 429 rate limit"));
        when(agent.chat(MEMORY_ID, MESSAGE)).thenThrow(nested).thenReturn("ok");

        assertThat(router().chat(MEMORY_ID, MESSAGE)).isEqualTo("ok");
        verify(agent, times(2)).chat(MEMORY_ID, MESSAGE);
    }

    @Test
    void chat_errorWithoutMessage_isTreatedAsNonTransient() {
        when(agent.chat(MEMORY_ID, MESSAGE)).thenThrow(new RuntimeException((String) null));

        assertThatThrownBy(() -> router().chat(MEMORY_ID, MESSAGE))
                .isInstanceOf(AiUnavailableException.class);

        verify(agent, times(1)).chat(MEMORY_ID, MESSAGE);
    }

    @Test
    void chat_transientDetectionIsCaseInsensitive() {
        when(agent.chat(MEMORY_ID, MESSAGE))
                .thenThrow(transientError("Service UNAVAILABLE right now"))
                .thenReturn("ok");

        assertThat(router().chat(MEMORY_ID, MESSAGE)).isEqualTo("ok");
        verify(agent, times(2)).chat(MEMORY_ID, MESSAGE);
    }
}
