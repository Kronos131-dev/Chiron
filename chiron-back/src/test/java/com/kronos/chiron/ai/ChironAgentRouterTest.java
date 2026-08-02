package com.kronos.chiron.ai;

import com.kronos.chiron.entity.AiProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChironAgentRouterTest {

    private static final String MEMORY_ID = "conv-1";
    private static final String MESSAGE = "combien de séries ?";

    @Mock
    private ChironAgent mistral;
    @Mock
    private ChironAgent gemini;
    @Mock
    private ConversationMemoryManager memoryManager;

    private ChironAgentRouter routerWithGemini() {
        return new ChironAgentRouter(mistral, gemini, memoryManager);
    }

    private ChironAgentRouter routerWithoutGemini() {
        return new ChironAgentRouter(mistral, null, memoryManager);
    }

    private static RuntimeException transientError(String message) {
        return new RuntimeException(message);
    }

    @Test
    void geminiAvailable_geminiConfigured_isTrue() {
        assertThat(routerWithGemini().geminiAvailable()).isTrue();
    }

    @Test
    void geminiAvailable_noGeminiKey_isFalse() {
        assertThat(routerWithoutGemini().geminiAvailable()).isFalse();
    }

    @Test
    void forProvider_gemini_returnsGeminiWhenConfigured() {
        assertThat(routerWithGemini().forProvider(AiProvider.GEMINI)).isSameAs(gemini);
    }

    @Test
    void forProvider_gemini_fallsBackToMistralWhenNotConfigured() {
        assertThat(routerWithoutGemini().forProvider(AiProvider.GEMINI)).isSameAs(mistral);
    }

    @Test
    void forProvider_mistral_returnsMistral() {
        assertThat(routerWithGemini().forProvider(AiProvider.MISTRAL)).isSameAs(mistral);
    }

    @Test
    void chatWithFallback_firstAttemptSucceeds_returnsReplyWithoutTouchingMemory() {
        when(gemini.chat(MEMORY_ID, MESSAGE)).thenReturn("quatre");

        String reply = routerWithGemini().chatWithFallback(AiProvider.GEMINI, MEMORY_ID, MESSAGE);

        assertThat(reply).isEqualTo("quatre");
        verify(gemini).chat(MEMORY_ID, MESSAGE);
        verifyNoInteractions(memoryManager, mistral);
    }

    @Test
    void chatWithFallback_transientErrorThenSuccess_retriesOnTheSameAgent() {
        when(gemini.chat(MEMORY_ID, MESSAGE))
                .thenThrow(transientError("503 UNAVAILABLE"))
                .thenReturn("quatre");

        String reply = routerWithGemini().chatWithFallback(AiProvider.GEMINI, MEMORY_ID, MESSAGE);

        assertThat(reply).isEqualTo("quatre");
        verify(gemini, times(2)).chat(MEMORY_ID, MESSAGE);
        verify(memoryManager).reset(MEMORY_ID);
        verifyNoInteractions(mistral);
    }

    @Test
    void chatWithFallback_transientErrorTwice_fallsBackToMistral() {
        when(gemini.chat(MEMORY_ID, MESSAGE)).thenThrow(transientError("model is overloaded"));
        when(mistral.chat(MEMORY_ID, MESSAGE)).thenReturn("repli");

        String reply = routerWithGemini().chatWithFallback(AiProvider.GEMINI, MEMORY_ID, MESSAGE);

        assertThat(reply).isEqualTo("repli");
        verify(gemini, times(2)).chat(MEMORY_ID, MESSAGE);
        verify(mistral).chat(MEMORY_ID, MESSAGE);
    }

    @Test
    void chatWithFallback_resetsMemoryBeforeFallingBackToMistral() {
        when(gemini.chat(MEMORY_ID, MESSAGE)).thenThrow(transientError("deadline exceeded"));
        when(mistral.chat(MEMORY_ID, MESSAGE)).thenReturn("repli");

        routerWithGemini().chatWithFallback(AiProvider.GEMINI, MEMORY_ID, MESSAGE);

        verify(memoryManager, times(2)).reset(MEMORY_ID);
    }

    @Test
    void chatWithFallback_nonTransientError_doesNotRetryButStillFallsBack() {
        when(gemini.chat(MEMORY_ID, MESSAGE)).thenThrow(new RuntimeException("400 bad request"));
        when(mistral.chat(MEMORY_ID, MESSAGE)).thenReturn("repli");

        String reply = routerWithGemini().chatWithFallback(AiProvider.GEMINI, MEMORY_ID, MESSAGE);

        assertThat(reply).isEqualTo("repli");
        verify(gemini, times(1)).chat(MEMORY_ID, MESSAGE);
    }

    @Test
    void chatWithFallback_mistralFailing_neverFallsBackToItself() {
        when(mistral.chat(MEMORY_ID, MESSAGE)).thenThrow(transientError("503 unavailable"));

        assertThatThrownBy(() -> routerWithGemini()
                .chatWithFallback(AiProvider.MISTRAL, MEMORY_ID, MESSAGE))
                .isInstanceOf(AiUnavailableException.class);

        verify(mistral, times(2)).chat(MEMORY_ID, MESSAGE);
        verifyNoInteractions(gemini);
    }

    @Test
    void chatWithFallback_bothAgentsFail_throwsAiUnavailableCarryingTheLastCause() {
        RuntimeException mistralFailure = new RuntimeException("mistral down");
        when(gemini.chat(MEMORY_ID, MESSAGE)).thenThrow(transientError("503"));
        when(mistral.chat(MEMORY_ID, MESSAGE)).thenThrow(mistralFailure);

        assertThatThrownBy(() -> routerWithGemini()
                .chatWithFallback(AiProvider.GEMINI, MEMORY_ID, MESSAGE))
                .isInstanceOf(AiUnavailableException.class)
                .hasCause(mistralFailure);
    }

    @Test
    void chatWithFallback_geminiRequestedButNotConfigured_goesStraightToMistral() {
        when(mistral.chat(MEMORY_ID, MESSAGE)).thenReturn("mistral");

        String reply = routerWithoutGemini().chatWithFallback(AiProvider.GEMINI, MEMORY_ID, MESSAGE);

        assertThat(reply).isEqualTo("mistral");
        verify(mistral).chat(MEMORY_ID, MESSAGE);
    }

    @Test
    void chatWithFallback_transientKeywordNestedInCause_isStillRetried() {
        RuntimeException nested = new RuntimeException("call failed",
                new IllegalStateException("upstream returned 429 rate limit"));
        when(gemini.chat(MEMORY_ID, MESSAGE)).thenThrow(nested).thenReturn("ok");

        assertThat(routerWithGemini().chatWithFallback(AiProvider.GEMINI, MEMORY_ID, MESSAGE))
                .isEqualTo("ok");
        verify(gemini, times(2)).chat(MEMORY_ID, MESSAGE);
    }

    @Test
    void chatWithFallback_errorWithoutMessage_isTreatedAsNonTransient() {
        when(gemini.chat(MEMORY_ID, MESSAGE)).thenThrow(new RuntimeException((String) null));
        when(mistral.chat(MEMORY_ID, MESSAGE)).thenReturn("repli");

        routerWithGemini().chatWithFallback(AiProvider.GEMINI, MEMORY_ID, MESSAGE);

        verify(gemini, times(1)).chat(MEMORY_ID, MESSAGE);
        verify(memoryManager, never()).reset(MEMORY_ID + "-unused");
    }

    @Test
    void chatWithFallback_transientDetectionIsCaseInsensitive() {
        when(gemini.chat(MEMORY_ID, MESSAGE))
                .thenThrow(transientError("Service UNAVAILABLE right now"))
                .thenReturn("ok");

        assertThat(routerWithGemini().chatWithFallback(AiProvider.GEMINI, MEMORY_ID, MESSAGE))
                .isEqualTo("ok");
        verify(gemini, times(2)).chat(MEMORY_ID, MESSAGE);
    }
}
