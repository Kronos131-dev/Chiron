# Backend verification checklist

## Sequence
* [ ] `docker info` answers — Testcontainers needs it.
* [ ] `mvn spotless:apply` was run.
* [ ] `mvn compile` passes.
* [ ] The class covering the change was run on its own and passes.
* [ ] `mvn verify` passes end to end.
* [ ] The work was not reported as done on `mvn compile` or `mvn test` alone.

## Coverage of the change
* [ ] A new or changed service method has a unit test.
* [ ] A new or changed endpoint has a `@WebMvcTest`.
* [ ] A new repository query has a `@DataJpaTest`.
* [ ] A new migration was exercised by `FlywaySchemaValidationTest` under `mvn verify`.
* [ ] The refusal path of any ownership check is tested, not only the happy path.

## Hygiene
* [ ] `git status` shows nothing unintended.
* [ ] No file under `db/migration/` was modified — only newly added ones.
* [ ] A whole-file Spotless reformat, if any, is destined for its own `style:` commit.
* [ ] No debug logging, `System.out.println` or commented-out code was left behind.
* [ ] The application starts: `mvn spring-boot:run` reaches a listening state.
