---
name: explore-codebase
description: Navigates the Chiron monorepo to answer how something works, where a behaviour lives, or what a change would touch. Use before changing code in an unfamiliar area, when tracing a value from a screen to the database, or when deciding which files a change affects. Prescribes an entry point per question type, the controller to service to repository and component to ChironApi to controller traversals, the AI coach's own path, and the directories that must never be searched. Do not use when the file to change is already known, when following a documented procedure (see the add and verify skills), or when chasing a known failure (see debug-systematically).
---

# Find where something lives

A repository-wide grep here returns hundreds of hits from `chiron-back/target/`,
`chiron-front/node_modules/`, `chiron-front/dist/` and `chiron-front/android/app/build/`, and burns
context without answering the question. Enter through a known door and follow the call path.

## Procedures

**Step 1: Exclude the noise**
1. Never search `chiron-back/target/`, `chiron-front/node_modules/`, `chiron-front/dist/`,
   `chiron-front/android/app/build/`, `chiron-front/android/.gradle/`, `.idea/` or `.git/`.
2. Scope every search to `chiron-back/src/`, `chiron-front/src/` or
   `chiron-front/android/app/src/`.
   `chiron-front/android/app/src/main/java/com/kronos/chiron/` holds the native Android service that
   runs an outdoor run — GPS, voice announcements, the wake word. It is source, it is tested, and the
   pipeline compiles it; only its `build/` and `.gradle/` subdirectories are noise.
3. Prefer a scoped glob over a bare grep: the codebase is small enough that the right directory
   listing usually answers the question faster than a search.

**Step 2: Enter through the matching door**
1. Read `references/entry-points.md` and pick the entry point for the kind of question being asked.
2. The four traversals that answer almost everything are in that file: an API behaviour, a screen
   behaviour, a coach behaviour, and a stored value.

**Step 3: Follow the path, do not search sideways**
1. Backend: controller → service → repository → entity. The controller names the service, the service
   names the repository; each step is one file read.
2. Frontend: component → `service/chiron-api.ts` → the backend controller. Every call goes through the
   facade, so it is the single index of the whole API surface.
3. Coach: `ChironAgent` `@SystemMessage` names the tool in brackets → the `coach/tools/*Tools` component holding
   that method → the repository it calls.
4. Stored value: `entity/` for the field → `db/migration/` for the column → the DTO exposing it.

**Step 4: Read tests as compressed specifications**
1. A service's test states the rules more compactly than the service does — the ownership checks
   especially.
2. `chiron-api.spec.ts` states the exact URL, method and payload of the endpoints it covers.

**Step 5: Confirm before acting**
1. Confirm the file found is the one actually wired: a controller reached by a route in
   `SecurityConfig`, a component reached by a route in `app.routes.ts`, a tool named in the system
   prompt.
2. Code that exists but is unreachable is common enough here to check for.
3. Report what was found as a path — "the screen calls X, which calls Y, which reads Z" — rather than
   as a file list.
4. Confirm every item in `references/checklist.md`.

## Error Handling

* If a grep returns hundreds of hits, it was not scoped to `src/`. Re-run it scoped.
* If a French domain noun finds nothing, try the English technical name and the reverse: identifiers
  mix `Seance` and `Exercice` with `Controller` and `Repository`.
* If a frontend string cannot be found in a template, it is an i18n key — search `i18n/fr.ts` for the
  text and then the key in the templates.
* If a backend behaviour has no obvious controller, it may belong to the coach. Search the `coach/`
  package for the `@Tool` description instead.
* If a column exists in the database but nowhere in `entity/`, it is a leftover from a deleted
  migration — V34 to V36 were removed after being applied.
* If two implementations of the same thing exist, the one wired into `SecurityConfig`,
  `app.routes.ts` or the `ChironAgent` prompt is the live one.
* If `README.md` seems to answer the question, distrust it. It documents Angular 17, Java 17 and a
  Mistral-only setup, none of which is still true.
