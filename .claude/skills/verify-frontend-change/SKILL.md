---
name: verify-frontend-change
description: Verifies and diagnoses a chiron-front change before hand-off. Use before committing frontend work, when TypeScript reports an error, when the Angular build breaks, or when the Vitest suite fails. Covers the tsc, prettier, test and build sequence, the fact that npm run build defaults to the production configuration, the absence of ESLint in this project, the fact that the pipeline never runs npm test so a red suite reaches main unnoticed, and the symptom-to-cause mapping for the failures this application produces. Do not use for writing components or services (see add-angular-feature), for writing the tests themselves (see write-frontend-tests), or for backend verification (see verify-backend-change).
---

# Verify a frontend change

Two facts about this project's baseline decide what a green result means.

**There is no ESLint.** No config, no script, no dependency. Never report that lint passed.

**The pipeline never runs `npm test`.** The workflow builds the frontend and stops there, so a red
suite reaches `main` without turning anything red. Run it locally, every time — nothing else will.

**`npx tsc --noEmit -p tsconfig.json` does not cover the specs.** `tsconfig.json` excludes them;
they are compiled by `tsconfig.spec.json` during the test run. A green `tsc` says nothing about test
code.

Run everything from `chiron-front/`.

## Procedures

**Step 1: Type check the application code**
1. Run `npx tsc --noEmit -p tsconfig.json`.
2. This is the fast gate and it passes on a clean checkout, so any error here belongs to the change.
3. The config is strict, including `strictTemplates` and `noPropertyAccessFromIndexSignature`.

**Step 2: Check formatting on the touched files only**
1. Run `npx prettier --check <the files that changed>`.
2. Do **not** run `npx prettier --check .` as a gate: 74 files are unformatted on a clean checkout,
   and the whole-project result is meaningless.
3. The `PostToolUse` hook already formats each file as it is edited, so this normally passes. It
   catches files edited outside the tooling.

**Step 3: Build**
1. Run `npm run build`.
2. It builds the **production** configuration by default — swapping in `environment.prod.ts` and
   enabling the service worker — and writes `dist/chiron-front/browser/`, the directory the deploy
   rsyncs.
3. A `bundle initial exceeded maximum budget` warning is pre-existing, around 1.05 MB against a 1 MB
   budget. It is a warning, not a failure; only report it if the change made it materially worse.

**Step 4: Run the tests**
1. Run `npm test`.
2. The whole suite must compile and pass: 42 tests over 11 files. A compile error in any spec stops
   the run before a single test executes, so a type error in a fixture reads as a green-looking
   silence, not as a failure of the spec you were writing.
3. Read `references/failure-modes.md` for anything else.
4. If the change added or modified a spec, that spec must run and pass. Apply the
   `write-frontend-tests` skill.

**Step 5: Look at it when it is visual**
1. Run `npm start` and open `http://localhost:4200`. A layout or styling change is not verified by a
   green build.
2. Check the narrow viewport too — the app is used on a phone mid-session and shipped through
   Capacitor.

**Step 6: Audit before hand-off**
1. Run `git status` and confirm nothing unintended was touched, in particular not
   `dist/`, `android/` or `node_modules/`.
2. Report honestly which of the four gates ran and what each returned.
3. Confirm every item in `references/checklist.md`.

## Error Handling

* If `npm test` stops on a type error in a fixture — a DTO interface gained a required field and the
  factories building it were not updated — fix every factory. `makeDef` in `session.spec.ts` and
  `programme-builder.spec.ts` is the one that has already been caught out this way.
* If `tsc` passes and `npm test` reports type errors, the errors are in spec files, which `tsc` does
  not compile.
* If a template error mentions a property that exists, `strictTemplates` is rejecting a nullable or a
  widened type. Narrow it in the component rather than casting in the template.
* If an index-signature access is rejected, `noPropertyAccessFromIndexSignature` requires bracket
  notation — this is how the flat i18n dictionaries are read.
* If a component test fails on an unknown element or pipe, the standalone component was not listed in
  the TestBed `imports`.
* If an HTTP call in a test never resolves, `provideHttpClientTesting()` is missing or the request was
  never flushed through `HttpTestingController`.
* If the build fails on a missing module after a dependency change, run `npm ci` rather than
  `npm install` to match the lockfile the CI uses.
* If the build succeeds but the browser shows an old screen, it is the service worker, not the build.
  `pwa-update.service.ts` prompts for a reload.
* If the deploy serves a stale bundle, compare the timestamps on the server with the
  `inspect-production` skill.
