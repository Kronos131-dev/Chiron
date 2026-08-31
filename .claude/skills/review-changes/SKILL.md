---
name: review-changes
description: Reviews the working diff in the Chiron repository for quality before committing. Use after finishing an implementation and before writing the commit, or when asked to check the work over. Reads the diff rather than whole files, and looks for the defects this codebase actually produces: comments added to production code, a bare RuntimeException, an entity returned from a controller, a component bypassing the ChironApi facade, an i18n key added to one dictionary only, an entity field with no Flyway migration, a hardcoded key, and an AI tool that is registered but absent from the system prompt. Do not use for build, type or test failures (see verify-backend-change or verify-frontend-change), for locating an unknown bug (see debug-systematically), or for writing the commit message itself (see commit-changes).
---

# Review the diff

Read the **diff**, not the files. A review that re-reads whole files costs context and finds
pre-existing issues that are not this change's business.

The defects worth hunting here are the ones with no compiler, test or annotation behind them: a
missing migration, a key in one dictionary, a tool the prompt never mentions, an ownership check
nobody wrote. Everything else the build already catches.

## Procedures

**Step 1: Get the diff**
1. Run `git status --porcelain` and `git diff` for unstaged work, `git diff --cached` for staged.
2. For work already committed on `main` but not yet pushed, `git diff @{u}..HEAD`.
3. Read it in full. A diff too large to read is a sign the change should have been several.

**Step 2: Walk the checklist against the diff**
1. Read `references/review-points.md` and go through it in order. It is organised by the failure the
   defect produces, not by file type.
2. Note each finding with the file and line, and whether it blocks.

**Step 3: Check the cross-file obligations**
1. These are the ones a single-file reading always misses:
   - an entity field added ⇒ a `V<n>` migration in the same diff;
   - an i18n key added ⇒ present in **both** `fr.ts` and `en.ts`;
   - an `coach/tools/*Tools` method added ⇒ named in the `ChironAgent` `@SystemMessage`;
   - a backend DTO field added ⇒ the interface in `chiron-api.ts` updated;
   - a required DTO field added ⇒ every test fixture building it updated;
   - a new endpoint ⇒ a `chiron-api.ts` method, and a `SecurityConfig` entry only if it is public.
2. Run `python3 .claude/skills/add-i18n-key/scripts/i18n-diff.py` if the diff touches either
   dictionary.

**Step 4: Check what should not be there**
1. Comments added under `chiron-back/src/main/java` or `chiron-front/src` — the hook blocks them
   during editing, but a file edited outside the tooling escapes it.
2. `console.log`, `System.out.println`, commented-out code, `TODO` and `FIXME`.
3. Any key, token, password or connection string. `chiron-back/.env` must not be staged.
4. Build output: `target/`, `dist/`, `node_modules/`, `android/app/build/`, `android/.gradle/`.
   `android/app/src/` is source, not output — it is reviewed like the rest.
5. A modified file under `db/migration/` that is not newly added.

**Step 5: Report**
1. List only what should change, with file and line. Do not inventory what is already correct.
2. Separate what blocks the commit from what is a suggestion.
3. If nothing blocks, say so in one line and move on to `commit-changes`.
4. Confirm every item in `references/checklist.md`.

## Error Handling

* If the diff is too large to read, propose splitting the work into several commits — `commit-changes`
  covers the seams.
* If `i18n-diff.py` reports divergences the diff did not cause, they are pre-existing. Name them for
  their own commit rather than folding them in.
* If a whole file appears reformatted, Spotless or Prettier normalised it on first contact. Confirm
  the real change inside it with `git diff -w`, and route the reformat to its own `style:` commit.
* If a finding needs the code rather than the diff to judge, read that one file. Do not re-read the
  whole change.
* If a defect is pre-existing and merely moved by the diff, say so and leave it — a review is not a
  cleanup mandate.
* If the diff touches `SecurityConfig`, `ChironAgent` or a migration, say so explicitly in the report.
  Those three reach production with consequences no test covers.
