# Frontend failure signatures

## The baseline on a clean checkout

Measured on `main`, with no local change. Know these before blaming a change.

| Command | Result |
|---------|--------|
| `npx tsc --noEmit -p tsconfig.json` | passes |
| `npm run build` | succeeds, with a `bundle initial exceeded maximum budget` warning (~1.05 MB against 1 MB) |
| `npx prettier --check .` | 74 files unformatted |
| `npm test` | 42 tests over 11 files, all green |
| ESLint | does not exist in this project |

The Angular builder compiles the whole spec graph before running anything, so a single spec that
does not type-check stops the entire suite — no test runs at all, and the output looks nothing like a
test failure. `TS2741: Property 'cardioType' is missing` did exactly that until the `makeDef`
factories in `session.spec.ts` and `programme-builder.spec.ts` were brought back in line with
`ExerciceDefinitionDto`.

## Type errors

| Symptom | Cause |
|---------|-------|
| `TS2741: Property 'x' is missing in type` in a spec | A DTO interface gained a required field; the test fixture was not updated |
| `TS2345` on a component method call in a spec | Same cause, seen from the call site |
| A template rejects a property that exists | `strictTemplates` is rejecting a nullable or widened type — narrow it in the component |
| `Property 'x' comes from an index signature` | `noPropertyAccessFromIndexSignature`; use `obj['x']`, as the i18n dictionaries do |
| `tsc` green but `npm test` reports type errors | `tsconfig.json` excludes the specs; `tsconfig.spec.json` compiles them |

## Test failures

| Symptom | Cause |
|---------|-------|
| `'app-x' is not a known element` | The standalone component is missing from the TestBed `imports` |
| `The pipe 't' could not be found` | `TranslatePipe` is standalone and must be imported by the test too |
| A test hangs on an HTTP call | `provideHttpClientTesting()` missing, or the request was never flushed |
| `Expected no open requests, found 1` | An `HttpTestingController.verify()` with an unflushed request |
| `NullInjectorError: No provider for ChironApi` | The facade was not stubbed in `providers` |
| A signal-based assertion reads a stale value | `fixture.detectChanges()` was not called after the change |

## Build failures

| Symptom | Cause |
|---------|-------|
| `Module not found` after a dependency change | Run `npm ci`, not `npm install`, to match the lockfile CI uses |
| `bundle initial exceeded maximum budget` | Pre-existing; only a concern if the change made it materially worse |
| The build emits to an unexpected path | `@angular/build:application` writes `dist/chiron-front/browser/`; the deploy rsyncs exactly that |
| A production-only failure | `npm run build` is already production; `npm start` is the development configuration |

## Runtime, in the browser

| Symptom | First check |
|---------|-------------|
| A screen shows the raw i18n key | The key is missing from the dictionary — `add-i18n-key` |
| The screen is stale after a deploy | Service worker; `pwa-update.service.ts` prompts for reload |
| A 401 on every API call | The token expired, or the call bypassed `chiron-api.ts` so the interceptor never saw it |
| A 403 on one endpoint only | Backend authorization, not the frontend — `add-api-endpoint` |
| Voice input does nothing | `webkitSpeechRecognition` exists only in Chromium |
| The chat reply renders as raw markdown | The `marked` pipe was bypassed |
