---
name: debug-systematically
description: Drives a disciplined debugging loop in the Chiron repository when behaviour is wrong and the cause is unknown: a failing test, a wrong value on screen, a silent no-op, an unexpected status code. Enforces reproduce, reduce, one hypothesis at a time, verify, fix the cause rather than the symptom, and leave a regression test. Covers the Chiron-specific causes worth ruling out first, since several integrations degrade silently rather than failing. Do not use when the error message already names the fix (see verify-backend-change or verify-frontend-change), when a pipeline is red (see diagnose-ci-failure), when the AI coach is the subject (see debug-ai-conversation), or when the production server holds the answer (see inspect-production).
---

# Chase an unknown cause

Guessing is the expensive path here. A backend iteration costs a full Maven cycle; a frontend one
costs a rebuild. Two or three disciplined passes beat a dozen speculative edits.

Chiron adds a specific trap: several integrations **degrade silently when unconfigured**. A blank
`GEMINI_API_KEY`, a false `VISBODY_MAILBOX_ENABLED`, an unreachable Olympus database all produce a
feature that does nothing, with no error anywhere — which reads exactly like a logic bug. Rule that
out before reading code.

## Procedures

**Step 1: Reproduce it**
1. Get the exact input, the exact observed output and the exact expected output. "It does not work" is
   not a reproduction.
2. Establish where it reproduces: locally, in production, or both. A production-only symptom is
   configuration or data, not logic — go to `inspect-production` or `manage-env-and-secrets`.
3. If it cannot be reproduced, stop and say so rather than fixing speculatively.

**Step 2: Rule out the silent causes**
1. Read `references/silent-causes.md` and check the ones that match the area. This is a short list and
   it explains a large share of "it does nothing" reports.
2. Confirm the code being read is the code being run — a stale container, a service worker serving an
   old bundle, or a test running against H2 rather than PostgreSQL.

**Step 3: Reduce the surface**
1. Cut the path down until the failure is in the smallest thing that still shows it: one test, one
   endpoint, one function.
2. Backend: write a failing unit test at the level of the suspect service or tool. It runs in seconds
   under `mvn -Dtest=<Class> test`, unlike the full build.
3. Frontend: reproduce in a spec rather than by clicking through the app.
4. A failing test at this point is the fastest feedback loop available, and it becomes the regression
   test in Step 6.

**Step 4: One hypothesis at a time**
1. State the hypothesis, the prediction it makes, and the observation that would falsify it.
2. Change **one** thing and re-run. Two changes at once make the result uninterpretable and usually
   cost another cycle.
3. Record what was ruled out. Re-testing the same hypothesis twice is the most common waste.
4. Prefer an observation over an edit: a log line, a value printed in a test, a `docker logs` read.

**Step 5: Fix the cause**
1. Fix where the wrong value originates, not where it surfaces. A null check at the screen hides a
   backend contract that is wrong.
2. If the cause is a convention the codebase already has — the exception mapping, the facade, the
   ownership check — apply it rather than working around it.
3. Remove every diagnostic added along the way: no `console.log`, no `System.out.println`, no debug
   logging left at `DEBUG`.

**Step 6: Leave the regression test**
1. Keep the failing test from Step 3 and make it pass. Apply `write-backend-tests` or
   `write-frontend-tests` to place and name it correctly.
2. Confirm it fails against the old code — a test that passes either way proves nothing.
3. Verify through `verify-backend-change` or `verify-frontend-change`.
4. Confirm every item in `references/checklist.md`.

## Error Handling

* If the symptom disappears without an identified cause, it is not fixed. Say so; an intermittent
  failure is usually shared state or ordering.
* If the cause is in `ai/`, the chat or the coach's answers, switch to `debug-ai-conversation`, which
  owns that path.
* If a test passes alone and fails in the suite, look for mutable static state or fixture data
  persisted in `@BeforeEach`.
* If the frontend shows stale data, check the service worker before the code — `pwa-update.service.ts`
  prompts for a reload.
* If an endpoint returns 403 for a user who should have access, the hand-written ownership check
  refused; there is no annotation to inspect.
* If a value is null after mapping, `SeanceMapper` is hand-written and may simply not map the field.
* If a repository query returns the wrong rows, read the derived method name character by character —
  the long ones are easy to misread.
* If the answer requires the production database or the live containers, stop guessing locally and
  apply `inspect-production`.
* If three hypotheses have failed, the model of the system is wrong rather than the code. Re-read the
  path with `explore-codebase` instead of continuing to iterate.
