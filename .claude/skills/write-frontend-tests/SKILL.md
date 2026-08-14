---
name: write-frontend-tests
description: Writes and fixes Vitest specs in chiron-front. Use when adding a spec for a standalone component, a signal-driven behaviour or a ChironApi method, or when replacing one of the generated should-create smoke tests with a real behaviour test. Covers Vitest 4 with explicit imports, TestBed.configureTestingModule for standalone components, stubbing the ChironApi facade, provideHttpClientTesting with HttpTestingController, and the pre-existing cardioType compile failure that currently stops the whole suite. Do not use for running the suite or diagnosing a build failure (see verify-frontend-change), for backend tests (see write-backend-tests), or for writing the component itself (see add-angular-feature).
---

# Write a frontend spec

The Angular builder compiles the **whole** spec graph before running anything, so one spec that does
not type-check stops the entire suite. That is the current state of `main`: `session.spec.ts` and
`programme-builder.spec.ts` build `ExerciceDefinitionDto` fixtures missing the required `cardioType`
field, and `npm test` therefore runs zero tests. Expect that failure, do not attribute it to the
change in progress, and read `verify-frontend-change` before concluding anything about the suite.

Most existing specs are still the generated "should create" smoke test. Replacing one with a real
behaviour test is an improvement.

## Procedures

**Step 1: Decide what is worth asserting**
1. Behaviour a user depends on: a signal that drives what the screen shows, a computed total, a
   filter, an error state.
2. The `ChironApi` facade's URL, method and payload, in `chiron-api.spec.ts`.
3. Not worth a spec: that a component instantiates, that a getter returns its field, that Angular
   renders a static string.

**Step 2: Place and name it**
1. Put the spec next to the code as `<name>.spec.ts`.
2. Name tests for the behaviour: `it('groups sessions by week', …)`, not `it('should work', …)`.

**Step 3: Write it**
1. Copy the matching skeleton from `assets/spec-skeletons.md`.
2. Import the Vitest functions explicitly — `import { describe, it, expect, vi, beforeEach } from 'vitest'`.
   `tsconfig.spec.json` declares `vitest/globals`, but the existing specs import explicitly and
   consistency matters more.
3. Register the standalone component in `imports`, never in `declarations`.
4. Stub `ChironApi` through `providers` with a `useValue` object returning `of(...)`. A component
   never calls `HttpClient` directly, so the facade is the only seam needed.
5. If the component uses the `| t` pipe, import `TranslatePipe` in the TestBed too — it is standalone.
6. Call `fixture.detectChanges()` after anything that changes a signal, before asserting on the DOM.

**Step 4: Keep fixtures honest**
1. Build DTO fixtures from the interface in `service/chiron-api.ts`, with every required field.
2. Prefer a small factory function in the spec over repeating an object literal — that is exactly
   what `makeDef` in `session.spec.ts` does, and it is why one missing field broke two specs at once.
3. When a DTO gains a required field, every fixture that builds it must be updated in the same
   change.

**Step 5: Test a `ChironApi` method**
1. Use `provideHttpClientTesting()` and `HttpTestingController`.
2. Assert the method, the URL and the body on the expected request, then `flush` the response and
   assert what the caller received.
3. Call `httpMock.verify()` in `afterEach` so an unflushed request fails the test.

**Step 6: Run it**
1. Run `npm test`.
2. If the run stops on the `cardioType` baseline failure, the new spec never executed. Either fix the
   fixtures as a separate change first, or verify the new spec by temporarily narrowing the run —
   and say which was done.
3. Apply the `verify-frontend-change` skill for the full sequence.
4. Confirm every item in `references/checklist.md`.

## Error Handling

* If the suite fails to compile on `cardioType`, that is the pre-existing baseline in
  `session.spec.ts` and `programme-builder.spec.ts`, not the new spec.
* If `'app-x' is not a known element`, the standalone component is missing from `imports`.
* If `The pipe 't' could not be found`, `TranslatePipe` is missing from `imports`.
* If `NullInjectorError: No provider for ChironApi`, the facade was not stubbed in `providers`.
* If a test hangs on an HTTP call, `provideHttpClientTesting()` is missing or the request was never
  flushed.
* If `Expected no open requests, found 1`, a request was made and not flushed — flush it or assert it.
* If a DOM assertion reads a stale value, `fixture.detectChanges()` was not called after the signal
  changed.
* If a stub returns a plain object where the component expects an `Observable`, wrap it in `of(...)`.
* If the spec type-checks in the editor but not in the run, the editor is using `tsconfig.json`, which
  excludes specs; the run uses `tsconfig.spec.json`.
