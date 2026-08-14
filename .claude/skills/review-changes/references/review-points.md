# What to look for in a Chiron diff

Organised by the failure the defect produces. Everything here is something no compiler, test or
annotation catches — the build already handles the rest.

## Fails at startup, in production

| Look for | Why |
|----------|-----|
| An entity field added with no `V<n>` migration in the same diff | `ddl-auto: validate` refuses to start |
| A modified file under `db/migration/` that is not newly added | Flyway checksum mismatch on every environment that ran it |
| A migration whose SQL type disagrees with the Java field | Schema validation failure |
| A dropped column still referenced in an entity, DTO or mapper | Schema validation failure |

## Silently wrong, nobody notices

| Look for | Why |
|----------|-----|
| An i18n key added to `fr.ts` but not `en.ts`, or the reverse | The screen renders the raw key for half the users |
| An `ai/*Tools` method added without a bracketed mention in the `ChironAgent` `@SystemMessage` | The coach never calls it, and nothing reports an error |
| A new `@Tool` returning JSON, an entity, or `null` | The model paraphrases it badly to the user |
| A new environment variable read in `application.yml` but absent from the workflow `printf` block | Works locally, degrades silently in production |
| A backend DTO field added without updating the `chiron-api.ts` interface | The frontend silently drops it |
| A `getIsPublic()` dereferenced without a null check | It is a nullable `Boolean` |

## Security

| Look for | Why |
|----------|-----|
| An endpoint reading another user's data with no check against the principal | There is no `@PreAuthorize` in this codebase; the check is the only protection |
| A `username` request parameter trusted as the caller's identity | It names the target, not the caller |
| A new entry in the `SecurityConfig` `permitAll` list | Everything there is reachable unauthenticated |
| Any key, token, password or connection string in a tracked file | |
| `chiron-back/.env` staged | It is gitignored and must stay so |

## Conventions the hooks cannot catch

| Look for | Why |
|----------|-----|
| A comment added under `chiron-back/src/main/java` or `chiron-front/src` | The hook blocks it during editing, but a file edited outside the tooling escapes |
| A bare `RuntimeException` | Only the four mapped exceptions produce a usable response |
| An entity returned from a controller | Lazy associations and password hashes leak |
| `@Transactional` on a controller or an `ai/` tool | The boundary lives in services only |
| A component injecting `HttpClient` instead of `ChironApi` | The JWT interceptor keys off the facade's URL |
| A new component with an inline template or a `Component` suffix | Against the established convention |
| A raw hex colour instead of a `@theme` token | Breaks the visual system |
| A new Lombok `@Builder` DTO class | DTOs are records; the `@Builder` ones are legacy |
| Field `@Autowired` instead of `@RequiredArgsConstructor` | |

## Test coverage of the change

| Look for | Why |
|----------|-----|
| A new ownership check with no test of its refusal path | The path with no framework behind it |
| A new service method with no unit test | |
| A new endpoint with no `@WebMvcTest` | |
| A new controller or repository test placed outside `controller/` or `repository/` | It would run under the wrong runner |
| A DTO gaining a required field without every test fixture updated | One missing field stops the whole frontend suite compiling |

## Leftovers

`console.log` · `System.out.println` · commented-out code · `TODO` · `FIXME` · debug logging left at
`DEBUG` · an unused import · a file under `target/`, `dist/`, `node_modules/` or `android/` staged.

## Worth calling out in the report even when correct

A diff touching `security/SecurityConfig.java`, the `ChironAgent` `@SystemMessage`, or a Flyway
migration reaches production with consequences no test covers. Name them explicitly so the user reads
those hunks before pushing.
