---
name: add-angular-feature
description: Builds or changes a screen in chiron-front. Use when adding a component under components/, wiring a route in app.routes.ts, adding a method to the ChironApi facade, holding state in signals, or styling with Tailwind. Covers the standalone component conventions, the no-Component-suffix naming, templateUrl and styleUrl rather than inline templates, the authGuard on every route, lazy loadComponent for heavy screens, the theme tokens in styles.css, and the requirement that every user-visible string goes through the t pipe with keys in both dictionaries. Do not use for the backend endpoint behind the screen (see add-api-endpoint), for adding a translation key alone (see add-i18n-key), or for writing its spec (see write-frontend-tests).
---

# Build a screen

Two conventions here are absolute and easy to break by habit.

**No component calls `HttpClient`.** `service/chiron-api.ts` is a single facade over every backend
call, and the JWT interceptor keys off `environment.apiUrl` — a direct call bypasses both.

**Every user-visible string goes through `| t`**, with the key in `fr.ts` *and* `en.ts`. A literal in
a template is invisible until a user switches language.

There is no store. State is `signal()` and `computed()`, and that is sufficient for this app.

## Procedures

**Step 1: Place the component**
1. Create `chiron-front/src/app/components/<feature>/` holding `<feature>.ts`, `<feature>.html` and
   `<feature>.css`.
2. Use kebab-case for the folder and the files, and no `Component` suffix on the class —
   `Chat`, `Journal`, `Session`. The `HeaderComponent` and `OnboardingComponent` names are legacy.
3. A component reused by several screens goes in `components/shared/`; a pure function goes in
   `shared/` or `util/`.

**Step 2: Write the component**
1. Copy the structure from `assets/component-skeleton.md`.
2. Declare it standalone with `templateUrl` and `styleUrl`. Never an inline template, never inline
   styles.
3. Hold state in `signal()`, derive with `computed()`, and never write a state field that a computed
   could produce.
4. Inject through the constructor (`constructor(private chironApi: ChironApi) {}`) — that is the
   convention in components here. `inject()` is used in guards, interceptors and pipes.
5. Handle the loading and error states explicitly. A screen that renders nothing while a call is in
   flight reads as broken on a phone.

**Step 3: Add the backend call to the facade**
1. Add a method to `service/chiron-api.ts` returning an `Observable`, typed by an interface mirroring
   the backend DTO field for field.
2. If the endpoint does not exist yet, build it first — apply the `add-api-endpoint` skill. The
   backend DTO is the contract.

**Step 4: Register the route**
1. Add an entry to `app.routes.ts` with `canActivate: [authGuard]`. Every route except `login` and
   `reset-password` carries it.
2. Use `loadComponent: () => import('./components/<feature>/<feature>').then(m => m.Feature)` for a
   heavy screen — charts, statistics, anything pulling a large dependency. The existing lazy routes
   are `exercice/:id`, `fitbit` and `statistics`.
3. Eager `component:` imports are correct for the core screens.
4. `''` and `**` already redirect to `chat`; leave them alone.

**Step 5: Style it**
1. Tailwind utilities only, using the `@theme` tokens declared in `src/styles.css` rather than raw
   hex values.
2. Leave the component's `.css` empty unless a genuine non-utility rule is needed.
3. Match the neighbouring screens: the look is dark and heroic, and the app is read on a phone
   mid-session. Check the narrow viewport.

**Step 6: Translate every string**
1. Add each key to `fr.ts` and `en.ts` and use `| t` in the template. Apply the `add-i18n-key` skill.
2. Import `TranslatePipe` in the component's `imports` array — it is standalone.
3. Run `python3 .claude/skills/add-i18n-key/scripts/i18n-diff.py` before finishing.

**Step 7: Verify**
1. Write a spec for the behaviour, not a smoke test. Apply the `write-frontend-tests` skill.
2. Run the sequence in the `verify-frontend-change` skill.
3. Open the screen in the browser, in both languages, on a narrow viewport.
4. Confirm every item in `references/checklist.md`.

## Error Handling

* If the screen renders a raw key like `journal.titre`, the key is missing from the active language's
  dictionary. Run `i18n-diff.py`.
* If the pipe is rejected with "could not be found", `TranslatePipe` is not in the component's
  `imports` — it is standalone, not global.
* If the API call returns 401, it bypassed `chiron-api.ts`, so the interceptor never attached the
  token.
* If it returns 403, the backend authorization refused the caller. That is `add-api-endpoint`, not a
  frontend problem.
* If a template rejects a property that exists, `strictTemplates` is refusing a nullable or widened
  type. Narrow it in the component rather than casting in the template.
* If a property access is rejected by `noPropertyAccessFromIndexSignature`, use bracket notation.
* If the screen does not update after data arrives, the value was assigned to a plain field instead of
  a signal, or `set`/`update` was not used.
* If the route renders nothing, the component was imported but not added to `routes`, or the guard
  redirected because the token expired.
* If the bundle budget warning grows materially, the screen pulled a heavy dependency eagerly — make
  the route lazy with `loadComponent`.
