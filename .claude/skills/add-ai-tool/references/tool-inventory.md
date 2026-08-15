# The eight tool components

All under `chiron-back/src/main/java/com/kronos/chiron/coach/`. All eight are passed as one
`Object[] tools` array to both the Mistral and the Gemini agent in `config/ChironConfig.java`.

| Component | Owns | Tools | Prompt block in `ChironAgent` |
|-----------|------|-------|-------------------------------|
| `WorkoutTools` | Sessions, exercises, sets, programmes, performance history, the exercise library, muscle coverage | 41 | SÉANCE · LECTURE PERFORMANCES · BIBLIOTHÈQUE · CRÉER UN PROGRAMME · ANALYSE/PLANIF · EXERCICES |
| `NutritionTools` | The Olympus nutrition integration | 10 | NUTRITION OLYMPUS |
| `AnalyseDieteTools` | Macro balance and diet analysis over a period | 6 | ANALYSE DIÉTÉTIQUE |
| `FitbitTools` | Sleep, steps, heart rate pulled from the Google Health API | 8 | FITBIT |
| `MemoryTools` | Durable notes about the athlete — injuries, preferences, goals, commitments | 6 | MÉMOIRE LONG-TERME |
| `RecoveryTools` | Daily readiness: sleep, fatigue, soreness, stress, energy | 6 | RÉCUPÉRATION |
| `AdaptiveTools` | Adapting the next session to recent load and state | 6 | PROGRAMMATION ADAPTATIVE |
| `AppGuideTools` | Explaining the application itself to the user | 1 | APP |

`WorkoutTools` is 1052 lines. Adding a workout capability there is still correct — splitting it would
mean a new prompt block and a new registration, and the model does not care about file size.

## Where the ownership rule already lives

`WorkoutTools` is the only component that reads other athletes' data, through a `targetUsername`
parameter. The rule is written inline at the top of each such tool — see
`getUserProgrammes` and `getUserHistory`:

- resolve the caller by id from `@ToolMemoryId`
- fall back to the caller's own username when `targetUsername` is null or blank
- allow when the caller *is* the target, or is `Role.ADMIN`
- otherwise require `targetUser.getIsPublic()` to be true
- return a French refusal sentence when it is not

There is no shared helper for this and no `@PreAuthorize` anywhere in the codebase. A new tool that
accepts a `targetUsername` must reproduce the block. A tool that only ever reads the caller's own data
needs nothing beyond resolving `userId`.

The coach relationship (`Utilisateur.coaches` / `coachedUsers`) is honoured by the REST layer but not
by every tool. Check the specific tool being copied rather than assuming.

## What the caller identity actually is

`@ToolMemoryId String userId` receives the memory id, which for Chiron is the **user's numeric id as
a string**, parsed with `Long.parseLong(userId)`. It is not the username and not the conversation id,
despite the memory itself being keyed by conversation — `ChatController` sets the association.

## Naming

Tool method names are camelCase and mostly French verbs (`enregistrerNote`, `rechercherExercices`,
`analyserCouvertureMusculaire`), with a few English ones inherited from the first version
(`startSession`, `addSet`, `endSession`, `getPersonalRecord`). Match the neighbouring names in the
component being extended rather than imposing one language.

## Returned text

Every tool returns a `String` the model paraphrases. The established shape is a short French sentence
carrying the numbers, and a plain sentence rather than an exception for "nothing found" or "not
allowed". `WorkoutTools` builds multi-line answers with a `StringBuilder` for lists — keep those under
a few dozen lines, because everything returned enters the context window of the next call.
