# AI tool checklist

## The method
* [ ] Lives in the component that already owns the domain.
* [ ] Annotated `@Tool("…")` with a French description naming the trigger, the parameters and the
      units.
* [ ] First parameter is `@ToolMemoryId String userId`; the caller is never passed as a username
      parameter.
* [ ] Parameters are flat scalars, with optional ones documented as such in the description.
* [ ] Returns a `String` — a short French sentence carrying the numbers. Never JSON, never an entity,
      never `null`.
* [ ] "Nothing found" returns a sentence, not an exception.
* [ ] No `@Transactional` on the tool; a multi-step write goes through a service.

## Access
* [ ] A tool accepting `targetUsername` reproduces the ownership block from
      `WorkoutTools.getUserProgrammes`: self, or `Role.ADMIN`, or a public profile.
* [ ] Refusal returns a French sentence rather than throwing.
* [ ] No lazy association is touched outside a transaction.

## Registration
* [ ] A new component is a constructor parameter of `chironAgentRouter` **and** in the
      `Object[] tools` array in `ChironConfig`.
* [ ] Both agents receive it — the array is shared and must stay shared.

## The prompt
* [ ] The tool name appears in brackets in the matching `ChironAgent` block, spelled exactly as the
      Java method.
* [ ] The trigger is phrased the way a user would say it, in French.
* [ ] The addition is one clause; no block was restructured.
* [ ] No prompt rule was weakened to accommodate the new capability.

## Verification
* [ ] A unit test covers the happy path, the empty path and the refusal path.
* [ ] `mvn test` passes.
* [ ] `mvn verify` passes.
* [ ] The coach was asked the real question in the chat and called the tool.
* [ ] A neighbouring question did **not** trigger it.
* [ ] If an entity field was added, the Flyway migration ships with it.
