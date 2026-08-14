# Symptom to layer

## The path a message takes

```
chat.ts (front)
  → POST /api/chat  { message, language, conversationId }
     → ChatController
          resolves or creates the Conversation
          seeds memory from the database if cold
          prepends: language directive
                    SYSTEM CONTEXT line (username, role)
                    [MÉMOIRE LONG-TERME …] — the 10 most recent ChironMemoryNote
       → ChironAgentRouter
            picks the agent from Utilisateur.aiProvider
            AiUsageService caps non-admins at 5 Gemini calls/day, else downgrades to Mistral
            retries transient failures twice, resetting memory each time, then falls back to Mistral
            throws AiUnavailableException → 503
         → ChironAgent (LangChain4j proxy, Mistral or Gemini)
              @SystemMessage — the whole coach personality and tool routing
              MessageWindowChatMemory(20), keyed by conversation id
            → ai/*Tools — the only database access the coach has
       → ConversationService.recordExchange — persists both messages
```

## Matching the report

| What the user says | Layer | First thing to check |
|--------------------|-------|----------------------|
| "It made up a number" | Prompt | Was any tool called in the log? If not, the prompt clause is missing or too vague |
| "It says it can't do that" but the tool exists | Prompt | Is the tool named in brackets in the matching `@SystemMessage` block? |
| "It ignores what I said two minutes ago" | Memory | Same conversation? The window is 20 messages, i.e. 10 exchanges |
| "It forgot everything after the deploy" | Memory | Cold start replays USER/AI text only, never tool results — by design |
| "It answers as a different personality" | Router | Gemini quota exhausted, silently downgraded to Mistral |
| "Gemini never answers" | Config | `GEMINI_API_KEY` blank ⇒ `ChironConfig` never built the Gemini agent |
| "Error 503" | Router | Both providers failed after retries; read the provider error above the exception |
| "Error 500" | Tools | Something other than `AiUnavailableException` escaped, usually a throwing tool |
| "It answers in English" | Controller | The `language` field did not reach `ChatController` |
| "It doesn't remember my injury" | Controller | Only the 10 most recent memory notes are prepended |
| "It calls the same tool over and over" | Prompt | Two clauses share a trigger, or the tool's return text reads as an instruction |
| "The reply is badly formatted" | Prompt or front | The prompt forbids tables and code blocks; the front renders through `marked` |
| "History panel is empty" | Persistence | `ConversationService` / `/api/conversations`, independent of the in-memory window |

## The retry classification

`ChironAgentRouter` retries only when the error message contains one of:

`503` · `unavailable` · `overloaded` · `timeout` · `deadline` · `429` · `rate limit`

`MAX_ATTEMPTS = 2`, backoff 400 ms × attempt, memory reset before each retry, then a fallback to
Mistral, then `AiUnavailableException`.

Anything else — a 401 from a bad key, a 400 from a malformed tool schema — fails on the first attempt.
That is deliberate: retrying a bad key just wastes 90 seconds of timeout.

## What is deliberate and should not be "fixed"

| Behaviour | Why |
|-----------|-----|
| Memory keyed by conversation, not by user | Opening a second conversation must not inherit the first |
| Replay omits tool calls | An orphaned tool request breaks the following call |
| Memory reset before each retry | The failed attempt's partial state would corrupt the retry |
| Gemini downgrade is silent | The cap is a cost control, not an error the user can act on |
| The window is 20 messages | Every message in it is re-sent on every call |
| Tools return prose, not JSON | The model paraphrases the answer to the user |
