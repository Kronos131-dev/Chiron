# Frontend verification checklist

## Sequence
* [ ] `npx tsc --noEmit -p tsconfig.json` passes.
* [ ] `npx prettier --check` passes on the files that changed.
* [ ] `npm run build` succeeds.
* [ ] `npm test` was run and its result reported honestly.

## Honesty about the baseline
* [ ] Lint was **not** claimed to pass — there is no ESLint in this project.
* [ ] A `cardioType` compile failure in `session.spec.ts` or `programme-builder.spec.ts` was
      identified as the pre-existing baseline, not attributed to this change.
* [ ] The suite was not reported as passing when it did not run.
* [ ] `npx prettier --check .` over the whole project was not used as a gate.
* [ ] The bundle budget warning was mentioned only if this change made it materially worse.

## Coverage of the change
* [ ] A new or changed component has a spec that runs and passes.
* [ ] A new `chiron-api.ts` method is covered in `chiron-api.spec.ts`.
* [ ] A new user-visible string has keys in both `fr.ts` and `en.ts`.

## Visual
* [ ] A layout or styling change was looked at in the browser, not only built.
* [ ] The narrow viewport was checked — the app is used on a phone.

## Hygiene
* [ ] `git status` shows nothing unintended; no `dist/`, `android/` or `node_modules/`.
* [ ] No `console.log` was left behind.
* [ ] No component calls `HttpClient` directly instead of going through `chiron-api.ts`.
