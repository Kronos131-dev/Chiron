---
name: debug-ai-conversation
description: Diagnoses the Chiron AI coach when it answers wrongly, forgets the conversation, ignores a tool, or returns 503. Use for any complaint about the chat itself rather than about a screen. Walks ChatController, ChironAgentRouter, ConversationMemoryManager and ConversationService, and covers the retry path, the single OpenRouter model behind every agent, memory keyed by conversation id, and why replay reinjects text without tool calls. Do not use for adding a capability the coach lacks (see add-ai-tool), for a backend build or test failure (see verify-backend-change), or for a frontend rendering problem in the chat component (see add-angular-feature).
---

# Debug the coach

Almost every "the coach is broken" report resolves to one of four things, and they are
distinguishable before reading any code: it was never told the tool exists, its memory was reset by
a retry, the model refused the prompt, or OpenRouter itself failed and the router exhausted its two
attempts. Establish which before forming a hypothesis — the layers look
similar from the outside and cost a lot to explore blind.

## Procedures

**Step 1: Get the exact exchange**
1. Ask for the literal message the user sent and the literal reply, not a summary. The trigger
   phrasing is what the model matched on.
2. Establish which conversation it happened in and whether it reproduces in a fresh one. Memory is
   keyed by conversation, so "it forgot" often means "different conversation".
3. Establish which provider the user is on — `Utilisateur.aiProvider`, changed in the settings screen.

**Step 2: Classify the symptom**
1. Read `references/symptom-map.md` and match the report to a layer before opening a file.
2. The four layers, in the order a message traverses them: `ChatController` builds the prompt
   context, `ChironAgentRouter` picks the model and retries, `ConversationMemoryManager` supplies the
   history, the `coach/tools/*Tools` components do the work.

**Step 3: Read the backend log for the exchange**
1. The model logs requests and responses — `logRequests(true)` and `logResponses(true)` in
   `ModeleIaConfig`.
2. Confirm from the log whether a tool was called at all. "Answered without calling the tool" and
   "called the tool and misreported it" are different bugs with different fixes.
3. In production, read it with the `inspect-production` skill.

**Step 4: If the coach did not call the tool**
1. Confirm the tool is named in brackets in the matching block of the `ChironAgent`
   `@SystemMessage`. A registered tool that the prompt never mentions is never called, and nothing
   reports it.
2. Confirm the trigger clause uses the words the user actually used.
3. Fix it through the `add-ai-tool` skill, which owns the prompt rules.

**Step 5: If the coach lost the thread**
1. Read `coach/agent/ConversationMemoryManager`. Memory is a `MessageWindowChatMemory.withMaxMessages(20)`
   held in a `ConcurrentHashMap` keyed by **conversation id**, not by user.
2. Twenty messages is ten exchanges. Losing context beyond that is the design, not a defect.
3. On a cold start the window is seeded from the database by replaying **USER and AI text only** —
   never tool calls. A coach that "forgets what it just looked up" after a restart is this, and it is
   deliberate: replaying an orphaned tool request breaks the next call.
4. `reset` purges and replays before each retry, so the second attempt loses whatever the failed one
   had accumulated.

**Step 6: If the reply is a 503**
1. `AiUnavailableException` reaching the client means the router exhausted everything:
   `MAX_ATTEMPTS = 2` with a 400 ms backoff per attempt. There is one model, so there is nothing to
   fall back to.
2. It only retries **transient** errors — the message must contain `503`, `unavailable`,
   `overloaded`, `timeout`, `deadline`, `429` or `rate limit`. Any other error fails on the first
   attempt by design.
3. Read OpenRouter's own error in the lines above the exception. A 401 is a bad
   `OPENROUTER_API_KEY` and a 404 on the model id is a renamed or withdrawn model, neither of which
   is an outage — apply `manage-env-and-secrets`.

**Step 7: If the model itself is the suspect**
1. `CHIRON_AI_MODEL` pins a dated version on OpenRouter. Confirm the id still exists and still
   advertises `tools` in its `supported_parameters` — the whole coach is function calling, and a
   model without tool support answers plausibly while writing nothing to the database.
2. A model swap changes behaviour with no code change and no log line saying so. Check the deployed
   value before blaming a prompt.

**Step 8: Reproduce and fix under test**
1. A tool that returns the wrong thing is ordinary Java — write a unit test against the tool class,
   as `coach/tools/WorkoutToolsTest` does. No model required.
2. A routing or memory bug is testable the same way against `ChironAgentRouter` and
   `ConversationMemoryManager` with mocked agents.
3. A prompt bug has no unit test. Verify it by conversation, on both providers, and confirm a
   neighbouring question does not now trigger the tool.
4. Confirm every item in `references/checklist.md`.

## Error Handling

* If the chat returns 503 immediately with no retry in the log, the provider error was not classified
  as transient. Read the message text against the list in Step 6.
* If the chat returns 500 rather than 503, something other than `AiUnavailableException` escaped —
  most likely a tool throwing.
* If the coach invents numbers, it answered without calling a tool. That is a prompt failure, not a
  model failure.
* If the coach repeats a tool call in a loop, two prompt clauses point at the same trigger, or the
  tool's return text reads like an instruction to call it again.
* If the coach answers in the wrong language, the `language` field from the frontend did not reach
  `ChatController`, which prepends the directive to the user message.
* If long-term notes are ignored, `ChatController` prepends only the ten most recent
  `ChironMemoryNote`s; an older note is simply not in the context.
* If a tool throws, the model receives the exception text and improvises around it. Return a sentence
  instead — see `add-ai-tool`.
* If a `LazyInitializationException` appears in a tool, it touched a lazy association outside a
  transaction.
* If the conversation history is missing from the sidebar but the coach still remembers, persistence
  in `ConversationService` failed while the in-memory window survived. Check
  `/api/conversations`.
