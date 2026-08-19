---
name: start-feature
description: Opens work on a new Chiron feature, fix or refactor. Use when a piece of work is described and coding has not begun, or when the scope and the affected side of the monorepo must be settled before writing code. Fixes the scope, locates the code, and routes to the implementation skill that owns the task. Chiron uses trunk-based development with no ticket tracker and no feature branches — all work happens directly on main, which deploys straight to production. Do not use once coding has begun, for finished work (see review-changes), or for shipping (see push-and-watch-pipeline).
---

# Open work on a change

There is no ticket tracker, no staging environment, and no feature branches — work happens directly
on `main`. The scope is whatever the user said, and `main` is production, so the one thing worth
doing before writing code is agreeing what "done" means.

## Procedures

**Step 1: Settle the scope**
1. Restate the change in one sentence, and say what it does **not** include.
2. Establish which side is affected: `chiron-back`, `chiron-front`, or both. If both, the backend goes
   first — its DTOs are the contract the frontend mirrors.
3. Identify what "done" looks like in the running app, not in the code. That sentence becomes the
   verification step later.
4. Ask only if a wrong assumption would waste the work. Otherwise state the assumption and continue.

**Step 2: Establish the operational consequences early**
1. Does it change the schema? Then it ships a Flyway migration that runs against production on
   deploy.
2. Does it need a new environment variable? Then it needs three entries before it works in
   production — apply the `manage-env-and-secrets` skill.
3. Does it change `security/SecurityConfig.java` or the `ChironAgent` system prompt? Both reach
   production with consequences no test covers.
4. Naming these now avoids discovering them at push time.

**Step 3: Confirm the tree is ready**
1. Run `git status` and `git fetch` so the work starts from current `main` with nothing uncommitted
   left behind by a prior task.
2. If the tree is dirty with unrelated work, deal with it first — the guard blocks `git stash` and the
   destructive verbs, so uncommitted work must be committed or handed back to the user.

**Step 4: Locate the code**
1. Apply the `explore-codebase` skill rather than grepping the whole repository.
2. Read the convention file for the side being changed: `.claude/conventions/chiron-back.md` or
   `.claude/conventions/chiron-front.md`.

**Step 5: Route to the skill that owns the task**

| The change is | Skill |
|---------------|-------|
| A REST endpoint | `add-api-endpoint` |
| A schema change | `add-flyway-migration` |
| A new coach capability | `add-ai-tool` |
| A screen | `add-angular-feature` |
| A translated string | `add-i18n-key` |
| A bug with a known cause | the skill owning that area |
| A bug with an unknown cause | `debug-systematically` |
| The coach answering wrongly | `debug-ai-conversation` |
| An integration doing nothing | `manage-env-and-secrets` |

Several may apply in sequence — an endpoint plus a migration plus a screen is three skills, in that
order.

**Step 6: Close the loop**
1. Tests: `write-backend-tests` or `write-frontend-tests`.
2. Verification: `verify-backend-change` or `verify-frontend-change`.
3. Self-review: `review-changes`.
4. Commit: `commit-changes`.
5. Ship: `push-and-watch-pipeline`.
6. Confirm every item in `references/checklist.md`.

## Error Handling

* If the scope covers both sides, do the backend first and finish it. A frontend built against an
  imagined DTO gets rewritten.
* If the scope grows while working, stop and say so. A change that outgrows its stated scope is two
  changes — commit what is done and restate scope for the rest.
* If the task is a one-line fix, this skill is overhead — locate the code and route directly.
* If the change turns out to need a schema change discovered mid-implementation, apply
  `add-flyway-migration` immediately: the application will not start without it, so it is not
  deferrable.
* If `main` has moved since `git fetch`, pull before committing. Never force-push; the guard blocks it
  and the deploy builds from what is on the remote.
