---
name: write-backend-tests
description: Writes and fixes tests in the chiron-back backend. Use when adding a test for a service, an AI tool, a controller, a repository or a migration, when choosing between a unit test and a Spring slice, or when deciding what to assert. Covers JUnit 5 with Mockito and AssertJ, the WebMvcTest slice with SecurityAutoConfiguration excluded, DataJpaTest on H2, the Testcontainers migration test, the Spring Boot 4 split test starters whose imports differ from Boot 3, the method_scenario_expectation naming, and the Given/When/Then comments that are the one place comments are allowed. Do not use for running the build or diagnosing a failing suite (see verify-backend-change), for frontend tests (see write-frontend-tests), or for production code (see add-api-endpoint).
---

# Write a backend test

The package a test lives in decides which runner executes it, and therefore whether it runs at all in
a given command. `service/`, `service/impl/`, `model/`, `coach/`, `mapper/`, `security/`, `client/` and
`visbody/` run under Surefire on `mvn test`; `controller/`, `persistence/` and `migration/` run under
Failsafe on `mvn verify`. Putting a slice test in the wrong package means it never runs in CI's
`test-unit` job and fails confusingly in `test-integration`.

This is Spring Boot **4.x**. The test-slice annotations come from split starters, so an import copied
from a Boot 3 example will not resolve.

## Procedures

**Step 1: Choose the kind of test**
1. A service, an AI tool, a mapper, an enum or a pure helper → a plain unit test with Mockito. Fast,
   no Spring context, runs on `mvn test`.
2. An endpoint's mapping, status codes and payload → `@WebMvcTest` in `controller/`.
3. A repository query, especially a long derived name → `@DataJpaTest` in `persistence/`.
4. A schema change → nothing new; `migration/FlywaySchemaValidationTest` already replays every
   migration.
5. Read `references/test-kinds.md` when the choice is not obvious.

**Step 2: Place and name it**
1. Mirror the production package under `src/test/java/com/kronos/chiron/`.
2. Name the class `<Subject>Test` — there is no `*IT` convention here, and the package alone routes it
   to the right runner.
3. Name methods `method_scenario_expectation`, as in
   `getHistorique_noSessions_returnsEmptyArray` and
   `creerProgramme_unknownExercise_throws`.

**Step 3: Write it**
1. Copy the matching skeleton from `assets/test-skeletons.md`.
2. Structure the body `// Given`, `// When`, `// Then`. These are the **only** comments allowed
   anywhere in the codebase — the no-comments hook exempts `src/test/java`.
3. Assert with AssertJ (`assertThat`), not JUnit's bare assertions.
4. Assert on the value, not on the number of interactions, unless the interaction *is* the behaviour.
5. Build entities with their Lombok builders, as the production code does.

**Step 4: Cover the paths that actually break**
1. The empty result — a repository returning nothing, a list with no rows.
2. The refusal — the hand-written ownership check rejecting a caller. This is the path with no
   framework behind it and therefore the one most worth testing.
3. The boundary — a null `getIsPublic()`, a zero-length list, a date with no entry.
4. For an AI tool, the sentence returned in each of those cases: the model relays it verbatim.

**Step 5: Run it**
1. Run the class alone: `mvn -Dtest=<Class> test`. An explicit `-Dtest` reaches any class, including
   one in an excluded package.
2. Then run the gate: `mvn verify`. Apply the `verify-backend-change` skill for the full sequence and
   the failure map.

**Step 6: Audit**
1. Confirm every item in `references/checklist.md`.

## Error Handling

* If `@WebMvcTest` returns 401 instead of the expected status, the slice loaded the security chain.
  Add `excludeAutoConfiguration = SecurityAutoConfiguration.class`.
* If `@WebMvcTest` cannot serialise the response, add `@Import(JacksonAutoConfiguration.class)`.
* If `@MockBean` does not resolve, it was removed in Boot 4 — use `@MockitoBean`.
* If `@WebMvcTest` or `@DataJpaTest` does not resolve, the import is the Boot 3 one. They now live in
  `org.springframework.boot.webmvc.test.autoconfigure` and
  `org.springframework.boot.data.jpa.test.autoconfigure`.
* If Mockito reports `UnnecessaryStubbingException`, the code no longer takes that path — delete the
  stub rather than loosening the test.
* If a `@DataJpaTest` fails on SQL that works in production, it is running on H2 in PostgreSQL mode;
  PostgreSQL-specific SQL belongs in a Testcontainers test instead.
* If `detached entity passed to persist` appears, a child was built before its parent was persisted.
  Persist the parent first.
* If a `LazyInitializationException` appears in an assertion, the association was touched outside the
  session — fetch it in the query.
* If a test passes alone and fails in the suite, it shares mutable static state or persists fixture
  data another test reads.
* If `Could not find a valid Docker environment` appears, the class needs Testcontainers — start
  Docker.
