---
name: add-api-endpoint
description: Adds or changes a REST endpoint in the chiron-back Spring Boot backend and exposes it to the Angular frontend. Use when creating a route, defining a request or response DTO, wiring a service and a Spring Data repository, or changing an existing endpoint's shape. Covers the controller-service-repository layering, DTOs as Java records, mapping without MapStruct, registering the path in SecurityConfig, error handling through GlobalExceptionHandler, verifying the caller's identity by hand since there is no PreAuthorize, and mirroring the endpoint in service/chiron-api.ts. Do not use for a capability the AI coach should call (see add-ai-tool), for schema changes (see add-flyway-migration), or for building the screen that consumes it (see add-angular-feature).
---

# Add a REST endpoint

Two things in this codebase are not what a Spring developer expects.

**There is no `@PreAuthorize` anywhere.** Authorization is a list of URL patterns in
`security/SecurityConfig.java`, and everything finer — this athlete's data, this coach's pupil — is
hand-written inside services. A new endpoint that forgets the check is not caught by a test or an
annotation; it simply serves another user's data.

**There is no MapStruct.** `mapper/SeanceMapper` is hand-written and is the only mapper. Small
conversions live in the service or in the DTO itself.

## Procedures

**Step 1: Place the endpoint**
1. Read `references/layering.md` and decide whether the feature belongs in the layered core
   (`controller/` + `service/` + `repository/`) or in one of the vertical slices (`stats/`, `fitbit/`,
   `nutrition/`, `visbody/`, `boditrax/`), where the controller, service and DTOs sit together.
2. Extend the existing controller for the domain rather than creating a sibling. `JournalController`,
   `ProgrammeController` and `ProfileController` each own a coherent surface.
3. Keep the `/api` prefix — every controller carries it in its `@RequestMapping`.

**Step 2: Define the DTOs**
1. Write request and response DTOs as Java `record`s in `dto/`, or in the slice's own package.
2. Never return an entity from a controller. `Seance`, `Exercice` and `Utilisateur` carry lazy
   associations and password hashes.
3. Name them `<Thing>Dto`, and put auth, chat and settings DTOs in their existing subpackages.
4. Do not add a Lombok `@Builder` DTO class; the few that exist are legacy.
5. Copy the shape from `assets/endpoint-layers.md`.

**Step 3: Write the repository query**
1. Add the method to the existing Spring Data interface in `repository/`.
2. Prefer a derived query name; the codebase already uses long ones
   (`findByUtilisateurUsernameAndIsModeleFalseOrderByDisplayOrderAscStartTimeDesc`).
3. Return `Optional<T>` for a single result and `List<T>` for many. Never return `null`.
4. If the read walks a lazy association, fetch it in the query rather than touching it later.

**Step 4: Write the service**
1. Put the business logic in a `@Service`, with `@RequiredArgsConstructor` for injection.
2. Annotate the write methods `@Transactional`. The transactional boundary lives here and nowhere
   else — never in a controller, never in an `ai/` tool.
3. Throw `NoSuchElementException` for a missing entity, `IllegalArgumentException` for bad input and
   `SecurityException` for a refused access. `GlobalExceptionHandler` maps them to 404, 400 and 403.
   Never throw a bare `RuntimeException`.

**Step 5: Verify the caller by hand**
1. Take the caller from the `Authentication` parameter, which Spring injects into a controller
   method. Do not trust a `username` request parameter, even though many existing endpoints accept
   one.
2. Apply the same rule the rest of the app applies: the caller is the owner, or is `ROLE_ADMIN`, or
   the target profile is public, or the caller is one of the target's coaches.
3. Read `references/authorization.md` for where each variant of that check already exists.

**Step 6: Register the path if it must be reachable unauthenticated**
1. Everything not listed in `SecurityConfig` requires authentication and answers 403 — which is the
   correct default. Change nothing for a normal endpoint.
2. Only add to the `permitAll` list for something genuinely public: an OAuth callback, an image, a
   health probe.
3. An endpoint that answers 403 in a working session is almost always this, not a bug in the code.

**Step 7: Expose it to the frontend**
1. Add a method to `chiron-front/src/app/service/chiron-api.ts`, returning an `Observable` typed with
   an interface mirroring the DTO field for field.
2. No component calls `HttpClient` directly; the facade is the whole contract.
3. The interceptor attaches the JWT to any URL containing `environment.apiUrl`, so nothing else is
   needed for authentication.

**Step 8: Test and verify**
1. Write a service unit test and a `@WebMvcTest` controller test. Apply the `write-backend-tests`
   skill — the controller test runs under `mvn verify`, not `mvn test`.
2. Run the sequence in the `verify-backend-change` skill.
3. Confirm every item in `references/checklist.md`.

## Error Handling

* If the endpoint answers 403 for an authenticated user, the URL is not matched by any `permitAll`
  rule and the code path is fine — or the hand-written ownership check rejected the caller. Read the
  service, not the security config, when the user should have had access.
* If it answers 401, the token is missing or expired; confirm the frontend call goes through
  `chiron-api.ts` so the interceptor sees it.
* If Jackson fails to serialise, an entity leaked into the response. Return the DTO.
* If a `LazyInitializationException` surfaces, the response is built outside the transaction. Fetch
  the association in the repository query.
* If the response carries fields the DTO does not declare, an entity is nested inside it. Map the
  nested object too.
* If the frontend receives `null` where a list was expected, the repository returned `null`; return an
  empty list.
* If the endpoint returns 500 with an opaque message, something threw a bare `RuntimeException`.
  Replace it with one of the four mapped exceptions.
* If the change added a field to an entity, the application will not start until the migration exists.
  Apply `add-flyway-migration`.
