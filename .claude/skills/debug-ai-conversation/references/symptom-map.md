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
            retries transient failures twice, resetting memory each time
            throws AiUnavailableException → 503
         → ChironAgent (LangChain4j proxy over the single OpenRouter model)
              @SystemMessage — the whole coach personality and tool routing
              MessageWindowChatMemory(20), keyed by conversation id
            → coach/tools/*Tools — the only database access the coach has
       → ConversationService.recordExchange — persists both messages
```

## Matching the report

| What the user says | Layer | First thing to check |
|--------------------|-------|----------------------|
| "It made up a number" | Prompt | Was any tool called in the log? If not, the prompt clause is missing or too vague |
| "It says it can't do that" but the tool exists | Prompt | Is the tool named in brackets in the matching `@SystemMessage` block? |
| "It ignores what I said two minutes ago" | Memory | Same conversation? The window is 20 messages, i.e. 10 exchanges |
| "It forgot everything after the deploy" | Memory | Cold start replays USER/AI text only, never tool results — by design |
| "It answers as a different personality" | Config | `CHIRON_AI_MODEL` was changed; a model swap leaves no log line saying so |
| "It answers but writes nothing" | Config | The pinned model lost tool support, or the id no longer exists on OpenRouter |
| "Error 503" | Router | OpenRouter failed after two attempts; read its error above the exception |
| "Error 500" | Tools | Something other than `AiUnavailableException` escaped, usually a throwing tool |
| "It answers in English" | Controller | The `language` field did not reach `ChatController` |
| "It doesn't remember my injury" | Controller | Only the 10 most recent memory notes are prepended |
| "It calls the same tool over and over" | Prompt | Two clauses share a trigger, or the tool's return text reads as an instruction |
| "The reply is badly formatted" | Prompt or front | The prompt forbids tables and code blocks; the front renders through `marked` |
| "History panel is empty" | Persistence | `ConversationService` / `/api/conversations`, independent of the in-memory window |

## The retry classification

`ChironAgentRouter` retries only when the error message contains one of:

`503` · `unavailable` · `overloaded` · `timeout` · `deadline` · `429` · `rate limit`

`MAX_ATTEMPTS = 2`, backoff 400 ms × attempt, memory reset before each retry, then
`AiUnavailableException`. One model, so no fallback to another provider.

Anything else — a 401 from a bad key, a 400 from a malformed tool schema — fails on the first attempt.
That is deliberate: retrying a bad key just wastes 90 seconds of timeout.

## What is deliberate and should not be "fixed"

| Behaviour | Why |
|-----------|-----|
| Memory keyed by conversation, not by user | Opening a second conversation must not inherit the first |
| Replay omits tool calls | An orphaned tool request breaks the following call |
| Memory reset before each retry | The failed attempt's partial state would corrupt the retry |
| The window is 20 messages | Every message in it is re-sent on every call |
| Tools return prose, not JSON | The model paraphrases the answer to the user |
