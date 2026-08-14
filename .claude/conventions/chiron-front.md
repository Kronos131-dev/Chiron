# chiron-front — conventions

Angular 21 with standalone components and Signals · Tailwind CSS 4 · Vitest 4 · Chart.js through
ng2-charts · `marked` for the coach's markdown replies · Capacitor 8 for the Android build · service
worker for the PWA. Builder is `@angular/build:application`. **There is no ESLint and no state
management library.**

The non-negotiable code style lives in the root `CLAUDE.md`, which is always loaded.

## Commands

Run from `chiron-front/`.

```bash
npm install                       # npm ci in CI
npm start                         # ng serve, http://localhost:4200, development configuration
npm run build                     # ng build — defaults to the PRODUCTION configuration
npm test                          # ng test -> @angular/build:unit-test -> Vitest
npx tsc --noEmit -p tsconfig.json # type check
npx prettier --write .            # the only formatter; printWidth 100, single quotes
npx cap sync && npx cap open android
```

`npm run build` swaps in `src/environments/environment.prod.ts` and enables the service worker, and
writes to `dist/chiron-front/browser/` — the path the deploy workflow rsyncs. There is no lint script;
never report a lint step as passing.

## Directory map

```
src/app/
  app.ts / app.html / app.css / app.config.ts / app.routes.ts
  components/<feature>/           one folder per screen: .ts + .html + .css (+ .spec.ts)
    shared/                       exercice-card, exercise-picker, header
  service/                        chiron-api.ts, auth.service.ts, active-session.service.ts,
                                  i18n.service.ts, translate.pipe.ts, pwa-update.service.ts
  security/                       auth.guard.ts (CanActivateFn), auth.interceptor.ts
  i18n/                           fr.ts, en.ts
  shared/                         pure helpers: exercise-filters, exercise-forms, tiers, tier-badges
  util/                           duration.ts
  environments/                   environment.ts (localhost:9090/api), environment.prod.ts
```

There is no `pages/` layer. Screens are components registered in `app.routes.ts`.

## Components

Standalone, declared with `templateUrl` and `styleUrl` — never an inline template or inline styles.
New classes take no `Component` suffix (`Chat`, `Journal`, `Session`, `Programme`); the few existing
`HeaderComponent` / `OnboardingComponent` names are legacy.

State is held in `signal()` and derived with `computed()`. Dependencies arrive through the
constructor in components (`constructor(private chironApi: ChironApi) {}`) and through `inject()` in
guards, interceptors and pipes.

## Data access

Every backend call goes through `service/chiron-api.ts`, a single `@Injectable({providedIn: 'root'})`
facade over `HttpClient` returning `Observable`s, with `private apiUrl = environment.apiUrl`. A
component never injects `HttpClient` directly. Adding an endpoint means adding a method there, and
mirroring the backend DTO shape in its return type.

`security/auth.interceptor.ts` attaches `Authorization: Bearer <token>` to any request whose URL
contains `environment.apiUrl`, so calls to other hosts stay unauthenticated by construction.

## Routing

All routes live in `app.routes.ts`. Every route except `login` and `reset-password` carries
`canActivate: [authGuard]`. `''` and `**` both redirect to `chat`. Heavy screens
(`exercice/:id`, `fitbit`, `statistics`) use `loadComponent` for lazy loading; the rest are eagerly
imported.

## Styling

Tailwind 4 utilities only. The design tokens are declared with `@theme` in `src/styles.css`; use them
rather than raw hex values. Do not add a stylesheet — a component's `.css` file exists but should stay
empty unless a genuine non-utility rule is needed. The look is dark and "heroic"; match neighbouring
screens.

## Internationalisation

Home-made, no library. `i18n/fr.ts` and `i18n/en.ts` export `Record<string, string>` with **flat
keys** in `namespace.cle` form. `fr.ts` is the source of truth; `en.ts` must mirror it key for key.
Interpolation is `{{param}}`, substituted by `I18nService.t(key, params)`. Templates use the `| t`
pipe (`TranslatePipe`), and `| localize` (`LocalizePipe`) for values that carry both a French and an
English form. A key present in one dictionary only renders as the raw key. Apply the `add-i18n-key`
skill.

## Testing

Vitest 4 with explicit imports — `import { describe, it, expect, vi, beforeEach } from 'vitest'` —
and `tsconfig.spec.json` declaring `"types": ["vitest/globals"]`. Specs sit next to the code as
`<name>.spec.ts`.

```ts
TestBed.configureTestingModule({
  imports: [ComponentUnderTest],
  providers: [{ provide: ChironApi, useValue: fake }],
});
```

`chiron-api.spec.ts` uses `provideHttpClientTesting()` with `HttpTestingController`. Many existing
specs are still the generated "should create" smoke test — replacing one with a real behaviour test is
an improvement, not a regression. Apply the `write-frontend-tests` skill.

## Gotchas

- `tsconfig.json` is strict, including `strictTemplates` and `noPropertyAccessFromIndexSignature`; an
  index-signature lookup needs bracket notation.
- The chat renders model output through `marked`; anything added there is HTML injected into the page.
- Voice input uses `webkitSpeechRecognition`, which exists only in Chromium browsers — guard it.
- Capacitor wraps the same build for Android with `appId com.kronos.chiron` and
  `webDir dist/chiron-front/browser`; CORS on the backend allows `capacitor://localhost` for it.
- The service worker caches aggressively. A stale screen after a deploy is usually
  `pwa-update.service.ts` not having prompted yet, not a build problem.
