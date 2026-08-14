# Choosing the kind of test

## The routing table

| Subject | Package | Annotations | Runner | Command |
|---------|---------|-------------|--------|---------|
| Service, AI tool, mapper, enum, helper | `service/`, `ai/`, `mapper/`, `entity/`, `util/`, `security/`, `fitbit/`, `visbody/` | `@ExtendWith(MockitoExtension.class)` | Surefire | `mvn test` |
| Endpoint mapping and payload | `controller/` | `@WebMvcTest(value = X.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)` + `@Import(JacksonAutoConfiguration.class)` | Failsafe | `mvn verify` |
| Repository query | `repository/` | `@DataJpaTest @ActiveProfiles("test")` | Failsafe | `mvn verify` |
| Schema | `migration/` | `@Testcontainers @SpringBootTest @ActiveProfiles("schema-it")` | Failsafe | `mvn verify` |

Existing counts: 6 service, 8 controller, 5 repository, 3 entity, 3 fitbit, 2 util, 1 each in `ai/`,
`mapper/`, `security/`, `visbody/`, `migration/`. 32 classes, 225 tests under `mvn test`.

The package is the only routing signal — every class is named `*Test`, and there is no `*IT`
convention.

## What each kind can and cannot prove

**Unit test.** Everything about the logic, including the ownership rules, which have no framework
behind them. This is where the security of an endpoint is actually verified.

**`@WebMvcTest`.** The URL mapping, the HTTP status, the JSON shape. It explicitly excludes
`SecurityAutoConfiguration`, so it proves **nothing** about authorization — a test passing here says
the payload is right, not that the caller was allowed.

**`@DataJpaTest`.** That a derived query name resolves and returns what its name promises. It runs on
H2 in PostgreSQL mode with `ddl-auto: create-drop` and Flyway disabled, so it does not exercise the
real schema and cannot validate PostgreSQL-specific SQL.

**`FlywaySchemaValidationTest`.** That every migration replays cleanly from V0 onto a real PostgreSQL
and that the entities validate against the result. It is the only test that touches the real database
engine.

## Test configuration

| Profile | File | Used by |
|---------|------|---------|
| `test` | `src/test/resources/application.yml` | `@DataJpaTest` — H2, `MODE=PostgreSQL`, `ddl-auto: create-drop`, Flyway off |
| `schema-it` | `src/test/resources/application-schema-it.yml` | The migration test — Testcontainers PostgreSQL, Flyway on, `ddl-auto: validate` |

## What is worth testing here

In descending order of value, given what this codebase actually gets wrong:

1. **The hand-written ownership checks.** No annotation enforces them; only a test does.
2. **The sentence an AI tool returns.** The model relays it to the user verbatim, including in the
   refusal and empty cases.
3. **The empty and boundary paths.** A nullable `getIsPublic()`, an empty list, a date with no entry.
4. **Derived query names.** The long ones are easy to get subtly wrong and compile fine.
5. **Mapping.** `SeanceMapper` is hand-written, so a forgotten field is silent.

Not worth testing: getters, builders, framework behaviour, or that Spring wires a bean.

## Naming

`method_scenario_expectation`, lowerCamelCase segments separated by underscores:

```
getHistorique_noSessions_returnsEmptyArray
creerProgramme_unknownExercise_throws
getEtatDuJour_noEntryToday_returnsExplanatorySentence
findByUtilisateurUsername_returnsOnlyThatUsersSessions
```

Structure the body with `// Given`, `// When`, `// Then`. These are the only comments permitted in
the codebase; the no-comments hook exempts `src/test/java` for exactly this.
