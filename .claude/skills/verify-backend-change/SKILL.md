---
name: verify-backend-change
description: Verifies and diagnoses a chiron-back change before hand-off. Use before committing backend work, when a Maven build fails, when a test class fails, or when the Spring application refuses to start. Covers the spotless, compile, test and verify sequence, the Surefire and Failsafe split that makes mvn test run only part of the suite, running a single test class or method, the Docker prerequisite for Testcontainers, and the symptom-to-cause mapping for the failures this project actually produces. Do not use for deciding what a test should assert (see write-backend-tests), for frontend verification (see verify-frontend-change), or for a failure that only happens in production (see inspect-production).
---

# Verify a backend change

`mvn test` does **not** run the whole suite. Surefire excludes `controller/`, `repository/` and
`migration/`; those run under Failsafe on `mvn verify`, and need a live Docker daemon. Reporting work
as verified on `mvn test` alone leaves the controller, repository and schema tests unrun — which is
precisely where an endpoint or a migration breaks.

Run everything from `chiron-back/`.

## Procedures

**Step 1: Start the infrastructure**
1. Run `docker info` and confirm the daemon answers. `FlywaySchemaValidationTest` starts a real
   PostgreSQL through Testcontainers and fails immediately without it.
2. Run `docker compose up -d db` from the repository root if the application itself needs to run.

**Step 2: Format**
1. Run `mvn spotless:apply`.
2. Spotless is bound to no lifecycle phase here, so this never happens on its own. The
   `PostToolUse` hook formats each file as it is edited; this catches anything edited outside it.
3. A file touched for the first time may come back with a whole-file reformat. That is expected —
   commit it separately with a `style:` subject, as the `commit-changes` skill describes.

**Step 3: Compile**
1. Run `mvn compile`.
2. A Lombok-related "cannot find symbol" on a getter means annotation processing did not run —
   confirm `lombok` is still in both the dependency list and the compiler plugin's
   `annotationProcessorPaths`.

**Step 4: Run the tests covering the change**
1. Run the specific class first: `mvn -Dtest=<Class> test`. An explicit `-Dtest` overrides the pom's
   exclusions, so this reaches a controller or repository test too.
2. To exercise it the way CI does, run it through Failsafe:
   `mvn -Dit.test=<Class> verify -DskipUTs=true`.
3. Read `references/test-selection.md` for the method and pattern syntax.
4. Fix failures at this level before running the whole suite.

**Step 5: Run the full build**
1. Run `mvn verify`. This is the hand-off gate: Surefire then Failsafe, including the Testcontainers
   schema validation.
2. Never report the work as done on `mvn compile` or `mvn test` alone.

**Step 6: Diagnose any failure**
1. Read `references/failure-modes.md` and match the symptom before changing anything.
2. Read the untruncated stack trace in `target/surefire-reports/<Class>.txt` or
   `target/failsafe-reports/<Class>.txt`. Console output is truncated; those files are not.
3. For an integration test, read the Spring startup log too — a context that failed to start reports
   as a cascade of unrelated errors, and only the first one is real.

**Step 7: Audit before hand-off**
1. Run `git status` and confirm nothing unintended was touched — in particular no file under
   `db/migration/` other than a newly added one.
2. Confirm every item in `references/checklist.md`.

## Error Handling

* If `mvn test` passes but `mvn verify` fails, the failure is in `controller/`, `repository/` or
  `migration/`. A plain `mvn test` runs 18 classes and 225 tests; those three packages are not among
  them.
* If any test reports `Could not find a valid Docker environment`, start Docker and re-run.
* If Testcontainers fails on a Docker API version, the `-Dapi.version=1.44` in the Failsafe `argLine`
  was lost — docker-java defaults to 1.32, which Docker 29 and later reject.
* If startup fails with `Schema-validation: missing column`, an entity changed without a migration.
  Apply the `add-flyway-migration` skill.
* If schema validation fails only in tests, the local database never replayed the new migration. Run
  `docker compose down -v && docker compose up -d db` from the repository root.
* If Flyway reports a checksum mismatch, an applied migration was edited. Restore it and add a new
  `V<n>`.
* If a `@WebMvcTest` returns 401 where 2xx was expected, the class is missing
  `excludeAutoConfiguration = SecurityAutoConfiguration.class` — the slice tests the mapping, not the
  security chain.
* If a `@WebMvcTest` cannot serialise the response, `@Import(JacksonAutoConfiguration.class)` is
  missing.
* If Mockito reports `UnnecessaryStubbingException`, delete the unused stub — it means the code no
  longer takes that path.
* If a test fails with `detached entity passed to persist`, a child was built before its parent was
  persisted. Apply the `write-backend-tests` skill.
* If a test passes alone but fails inside the suite, the class leaks state through a mutable static
  field or a `@BeforeEach` that persists shared data.
* If the build reports `Port already in use`, a previous run left containers up. Run
  `docker compose down` and remove stale Testcontainers.
* If google-java-format is proposed as a fix for a Spotless failure, it cannot run on the local
  JDK 25 — this project uses the Eclipse formatter with `chiron-back/eclipse-formatter.xml`.
