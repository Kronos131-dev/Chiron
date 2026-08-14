# Running one test rather than the suite

## Which runner owns the class

`chiron-back/pom.xml` splits the suite by package:

| Package | Runner | Command |
|---------|--------|---------|
| `service/`, `entity/`, `ai/`, `mapper/`, `security/`, `util/`, `fitbit/`, `visbody/` | Surefire | `mvn test` |
| `controller/`, `repository/`, `migration/` | Failsafe | `mvn verify` |

Surefire's `<excludes>` are `**/controller/**`, `**/repository/**`, `**/migration/**`. Failsafe's
`<includes>` are `**/controller/**Test.java`, `**/repository/**Test.java`, `**/migration/**Test.java`.

Everything is named `*Test.java` — there is no `*IT.java` convention here, so the package is the only
signal for which runner picks a class up.

## Selecting one class

An explicit `-Dtest` **overrides** the pom's `<excludes>`, so it reaches any class regardless of
package — including the controller, repository and migration tests. This is the shortest way to run a
single test, whatever it is.

```bash
mvn -Dtest=ProgrammeServiceTest test
mvn -Dtest=JournalControllerTest test          # works: -Dtest overrides the exclusion
mvn -Dtest=ProgrammeServiceTest#creerProgramme_unknownExercise_throws test
mvn -Dtest='ProgrammeServiceTest,JournalServiceTest' test
mvn -Dtest='*ServiceTest' test
mvn -Dtest='WorkoutToolsTest#addSet*' test
```

`FlywaySchemaValidationTest` still needs Docker whichever way it is selected.

## Selecting through Failsafe

The property is `it.test` and the phase is `verify`. Use this when the point is to exercise the class
the way CI does:

```bash
mvn -Dit.test=JournalControllerTest verify -DskipUTs=true
mvn -Dit.test=FlywaySchemaValidationTest verify -DskipUTs=true
mvn -Dit.test='*ControllerTest' verify -DskipUTs=true
```

`-DskipUTs=true` skips Surefire so only the integration tests run — the same flag the
`test-integration` CI job uses.

## What plain `mvn test` covers

18 classes, 225 tests: `service/` (6), `fitbit/` (3), `entity/` (3), `util/` (2), and one each in
`ai/`, `mapper/`, `security/`, `visbody/`. The `controller/` (8), `repository/` (5) and `migration/`
(1) classes are excluded and run only under `mvn verify`. A green `mvn test` therefore says nothing
about an endpoint or a migration.

## Useful combinations

```bash
mvn verify                                   # the hand-off gate: everything
mvn test                                     # unit tests only, no Docker needed
mvn verify -DskipUTs=true                    # integration tests only, Docker required
mvn -q -Dtest=ProfileServiceTest test        # quiet, one class
mvn verify -DskipTests                       # build the jar without testing
```

## Reading the result

Console output is truncated. The full stack trace is in:

- `target/surefire-reports/<Class>.txt`
- `target/failsafe-reports/<Class>.txt`

For a Spring integration test that failed to start its context, the first error in that file is the
real one; everything after it is the cascade.
