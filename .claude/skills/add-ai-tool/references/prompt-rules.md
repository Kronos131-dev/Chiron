# Editing the `ChironAgent` system prompt

`ai/ChironAgent.java` holds the whole coach personality and routing logic in a single
`@SystemMessage({...})` array of French strings. It is sent on **every** message, to both providers.
Every line added is paid for on every call, in latency and in tokens.

## The shape

- One `""` empty string separates blocks; blocks are named in capitals (`STYLE`, `SÉANCE`,
  `LECTURE PERFORMANCES`, `BIBLIOTHÈQUE`, `CRÉER UN PROGRAMME`, `ANALYSE/PLANIF`, `FIN DE SÉANCE`,
  `RÉCUPÉRATION`, `MÉMOIRE LONG-TERME`, `PROFIL SPORTIF`, `NUTRITION OLYMPUS`, `ANALYSE DIÉTÉTIQUE`,
  `FITBIT`, `PROGRAMMATION ADAPTATIVE`, `EXERCICES`, `APP`).
- Inside a block, capabilities are clauses separated by ` ; `, each of the form
  *trigger* ` → ` `[toolName]`.
- Tool names appear in square brackets and must match the Java method name exactly.
- `RÈGLE GÉNÉRALE` establishes the two invariants the rest depends on: always call the relevant tool
  before answering, and call `[getCurrentDate]` first for any relative date.

## Adding a capability

Add a clause to the existing block. A new block is justified only by a genuinely new domain, and
should still be one line.

```
"RÉCUPÉRATION : état du jour → [getEtatDuJour] ; tendance semaine → [getTendanceRecuperation]."
```

Write the trigger the way a user phrases it, not the way the code names it. The model matches on the
trigger; "tendance/évolution de la récupération" earns its place, "invoke recovery trend analysis"
does not.

## Rules that are load-bearing, and why

| Rule in the prompt | Why it exists |
|--------------------|---------------|
| `AUCUN émoji` | The product voice is a demanding mentor, not a chat assistant |
| Markdown limited to bold, `-` lists, `##`, `>` — no tables, no code blocks | The frontend renders replies through `marked` into a narrow mobile column; a table breaks the layout |
| 2 to 3 sentences maximum | Replies are read mid-set, on a phone |
| Never claim "no data" without calling a tool first | The failure mode that made the coach useless before the rule existed |
| `[getCurrentDate]` before any relative date | The model has no clock and will otherwise invent today's date |
| Never recommend an exercise absent from the library | Recommendations must resolve to a real `ExerciceDefinition` the app can display |
| `[creerProgramme]` once, after `[rechercherExercices]` | Calling it repeatedly created duplicate programmes |

Do not weaken these to make a new capability fit. If a capability needs a table, it does not belong
in the chat.

## Testing a prompt change

There is no unit test for the prompt. Verify by conversation:

1. Ask the question a user would ask, in French, without naming the tool.
2. Confirm the tool was called, in the backend log — `logRequests(true)` and `logResponses(true)` are
   on for Mistral, `logRequestsAndResponses(true)` for Gemini.
3. Ask a neighbouring question and confirm the tool is **not** called. An over-broad trigger makes the
   coach call it constantly, which is slower and costs more.
4. Test on both providers if `GEMINI_API_KEY` is set locally: the two models weigh the prompt
   differently, and a clause that works on Mistral can be ignored by Gemini.

## What not to do here

- Do not restructure or reorder blocks while adding a tool. The diff becomes unreviewable and the
  behaviour change is untraceable.
- Do not move the prompt into a resource file. `AiServices` reads it from the annotation, and the
  indirection would hide the one artefact that most determines the product's behaviour.
- Do not add per-user conditionals here. Per-user context is prepended by `ChatController` — the
  language directive, the `SYSTEM CONTEXT` line and the long-term memory block.
