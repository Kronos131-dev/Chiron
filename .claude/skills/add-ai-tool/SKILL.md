---
name: add-ai-tool
description: Gives the Chiron AI coach a new capability by adding a LangChain4j @Tool method to one of the coach/tools/*Tools components. Use when the coach should be able to read or write something it currently cannot, when a tool exists but the coach never calls it, or when editing the ChironAgent @SystemMessage. Covers choosing the right tool component, the @Tool and @ToolMemoryId signature, returning text a language model can use, enforcing ownership and coach rules inside the tool, registering the bean in ChironConfig, and declaring the tool in the system prompt. Do not use for a plain REST endpoint the frontend calls (see add-api-endpoint), for a coach that answers wrongly or forgets (see debug-ai-conversation), or for schema changes (see add-flyway-migration).
---

# Give the coach a new capability

A `@Tool` method is invisible to the model until it is **both** registered in `ChironConfig` **and**
named in the `ChironAgent` `@SystemMessage`. A method that compiles, has a perfect description and is
never mentioned in the prompt will simply never be called, and nothing anywhere reports an error. That
silence is what makes this task expensive.

The tools are the coach's only access to the database. There is no `@PreAuthorize` behind them —
whatever ownership rule the feature needs must be written inside the tool itself.

## Procedures

**Step 1: Choose the component**
1. Read `references/tool-inventory.md` and place the new tool in the component that already owns that
   domain. A new component means a new constructor argument in `ChironConfig` and a new prompt block,
   and is rarely warranted.
2. Read the neighbouring tools in that file before writing. They establish the naming, the return
   phrasing and the ownership checks for that domain.

**Step 2: Write the method**
1. Copy the structure from `assets/tool-skeleton.md`.
2. Annotate with `@Tool("description en français")`. The description is what the model reads to
   decide whether to call it — name the trigger, the parameters and the units.
3. Take the caller as `@ToolMemoryId String userId`, always the first parameter. It carries the
   conversation's user; never add a `username` parameter for the caller.
4. Keep parameters flat: `String`, `int`, `Integer`, `double`, `boolean`. A model fills these
   reliably; nested objects it does not. Use `null` or a blank string as "not specified" and say so
   in the description.
5. Return a `String` written for a model to paraphrase — a short factual sentence with the numbers in
   it. Never return JSON, never return an entity, never return `null`.
6. Return an explanatory sentence rather than throwing when the answer is simply "nothing found": the
   model relays it to the user. Reserve exceptions for genuine faults.
7. Annotate the class-level dependency injection with `@RequiredArgsConstructor` and never open a
   transaction here — call a service if the write needs one.

**Step 3: Enforce the access rule inside the tool**
1. Resolve the caller from `userId` through the repository, as the neighbouring tools do.
2. If the tool can read another athlete's data, apply the same rule the rest of the app applies:
   the target's profile must be public, or the caller must be one of the target's coaches, or the
   caller must be an admin. Read `references/tool-inventory.md` for where that check already lives.
3. Return a refusal sentence rather than throwing when the rule denies access.

**Step 4: Register the bean in `ChironConfig`**
1. If the tool went into an existing component, nothing is needed here — the component is already in
   the array. Skip to Step 5.
2. For a new component, add it as a constructor parameter of `chironAgentRouter` and add it to the
   `Object[] tools` array in `config/ChironConfig.java`.
3. There is a single agent, on the single OpenRouter model built by `ModeleIaConfig`. The array is
   the only place a tool becomes reachable.

**Step 5: Declare the tool in the system prompt — the step that makes it real**
1. Open `coach/agent/ChironAgent.java` and find the rule block that matches the domain (SÉANCE, LECTURE
   PERFORMANCES, BIBLIOTHÈQUE, ANALYSE/PLANIF, RÉCUPÉRATION, MÉMOIRE LONG-TERME, NUTRITION OLYMPUS,
   FITBIT, APP…).
2. Add the trigger and the tool name in the established form: a short French clause, then
   `→ [nomDeLOutil]`. Match the density of the surrounding lines; this prompt is sent on every single
   message, so every word costs latency and money.
3. If the capability needs its own block, add one and keep it to a single line.
4. Do not restructure the prompt while adding a tool. Read `references/prompt-rules.md` before
   changing anything that is not an addition.

**Step 6: Test the tool without the model**
1. Write a unit test against the tool class directly, as `coach/tools/WorkoutToolsTest` does — mock the
   repositories, call the method, assert on the returned sentence.
2. Cover the refusal path and the empty-result path, not just the happy one. Apply the
   `write-backend-tests` skill.
3. Run `mvn test`. A tool is ordinary Java; it does not need a model to be tested.

**Step 7: Verify end to end**
1. Run `mvn verify` through the `verify-backend-change` skill.
2. Start the application and ask the coach, in the chat, the question a user would actually phrase.
   If it answers without calling the tool, the prompt line is missing or too vague — return to
   Step 5.
3. Confirm every item in `references/checklist.md`.

## Error Handling

* If the coach never calls the tool, the prompt does not name it. Confirm the exact tool name appears
  in brackets in `ChironAgent`, spelled identically to the method.
* If the coach calls it with wrong or missing arguments, the `@Tool` description does not state the
  units or which parameters are optional. Rewrite the description, not the prompt.
* If the coach calls it constantly and unprompted, the trigger clause in the prompt is too broad.
  Narrow it to the phrasing a user would really use.
* If the tool throws, the model receives the exception text and improvises around it. Return a
  sentence instead.
* If the tool returns JSON, the model paraphrases it badly and leaks field names to the user. Return
  prose with the numbers in it.
* If the tool is never called, the registration is only half the story — the prompt must name it in
  brackets. Read the `debug-ai-conversation` skill before touching the tool itself.
* If a `LazyInitializationException` surfaces, the tool touched a lazy association outside a
  transaction. Fetch what is needed in the repository query, or call a service that owns the
  transaction.
* If the change added a field to an entity, `ddl-auto: validate` refuses to start until the migration
  exists. Apply `add-flyway-migration`.
