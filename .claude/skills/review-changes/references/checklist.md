# Review checklist

## Method
* [ ] The diff was read, not whole files.
* [ ] Findings name the file and the line.
* [ ] Blocking findings are separated from suggestions.
* [ ] Nothing already correct was inventoried — the report lists only what should change.
* [ ] Pre-existing defects merely moved by the diff were named as such, not folded in.

## Cross-file obligations
* [ ] An entity field added has its `V<n>` migration in the same diff.
* [ ] An i18n key exists in both `fr.ts` and `en.ts`; `i18n-diff.py` was run if either was touched.
* [ ] A new `@Tool` is named in the `ChironAgent` `@SystemMessage`.
* [ ] A backend DTO change is mirrored in `chiron-api.ts`.
* [ ] A DTO's new required field was added to every test fixture building it.
* [ ] A new endpoint has a `chiron-api.ts` method.

## Security
* [ ] Every endpoint touching another user's data checks the principal.
* [ ] No `username` parameter is trusted as the caller.
* [ ] `SecurityConfig` gained no entry unless the endpoint is genuinely public.
* [ ] No key, token, password or connection string appears anywhere in the diff.
* [ ] `chiron-back/.env` is not staged.

## Conventions
* [ ] No comment was added to production code.
* [ ] No bare `RuntimeException`, no entity returned from a controller.
* [ ] `@Transactional` appears only in services.
* [ ] No component calls `HttpClient` directly.
* [ ] No raw hex colour, no inline template, no new `@Builder` DTO class.

## Leftovers
* [ ] No `console.log`, `System.out.println`, commented-out code, `TODO` or `FIXME`.
* [ ] No build output is staged.
* [ ] No already-applied migration was modified.

## Closing
* [ ] A diff touching `SecurityConfig`, the `ChironAgent` prompt or a migration was flagged
      explicitly.
* [ ] The work was handed to `commit-changes` once nothing blocks.
