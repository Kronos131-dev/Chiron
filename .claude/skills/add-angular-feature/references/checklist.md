# Screen checklist

## Structure
* [ ] The folder is `components/<feature>/` with `.ts`, `.html` and `.css`, kebab-case.
* [ ] The class is standalone, with `templateUrl` and `styleUrl` — no inline template or styles.
* [ ] No `Component` suffix on the new class name.
* [ ] A component reused elsewhere lives in `components/shared/`.

## State and data
* [ ] State is held in `signal()`; derived values use `computed()`.
* [ ] Loading and error states are handled explicitly.
* [ ] The error signal holds an i18n key, not a sentence.
* [ ] Dependencies are injected through the constructor.
* [ ] Every backend call goes through `service/chiron-api.ts`; no `HttpClient` in the component.
* [ ] The TypeScript interface mirrors the backend DTO field for field.

## Routing
* [ ] The route is registered in `app.routes.ts`.
* [ ] `canActivate: [authGuard]` is present.
* [ ] A heavy screen uses `loadComponent`, not an eager import.

## Styling
* [ ] Tailwind utilities only; the component `.css` stayed empty unless genuinely needed.
* [ ] Colours come from the `@theme` tokens in `src/styles.css`, not raw hex.
* [ ] The screen was checked on a narrow viewport.

## Text
* [ ] Every user-visible string goes through `| t`.
* [ ] `TranslatePipe` is in the component's `imports`.
* [ ] Keys exist in both `fr.ts` and `en.ts`.
* [ ] `i18n-diff.py` exits 0.

## Verification
* [ ] A spec covers a real behaviour, not just instantiation.
* [ ] `npx tsc --noEmit -p tsconfig.json` passes.
* [ ] `npm run build` succeeds and the bundle budget did not grow materially.
* [ ] The screen was opened in the browser in both languages.
