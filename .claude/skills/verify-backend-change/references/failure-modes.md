# Backend failure signatures

Match the symptom before changing anything.

## Build and formatting

| Symptom | Cause | Fix |
|---------|-------|-----|
| `cannot find symbol: method getTitre()` | Lombok annotation processing did not run | Confirm `lombok` is in the dependencies **and** in `annotationProcessorPaths` |
| `NoSuchMethodError: Log$DeferredDiagnosticHandler.getDiagnostics` from Spotless | google-java-format cannot run on JDK 25 | This project uses the Eclipse formatter; do not swap the formatter back |
| A whole file reformatted on first edit | Spotless normalising a file the codebase never formatted | Expected; commit separately with a `style:` subject |
| Compilation succeeds locally, fails in CI | Local JDK is 25, CI builds on 21 | Avoid APIs newer than Java 21 |

## Spring startup

| Symptom | Cause | Fix |
|---------|-------|-----|
| `Schema-validation: missing column [x] in table [y]` | Entity field with no migration | `add-flyway-migration` |
| `Schema-validation: wrong column type` | SQL type and Java type disagree | Correct the migration or the field |
| `Validate failed … checksum mismatch` | An applied migration was edited | Restore it, add a new `V<n>` |
| `Detected resolved migration not applied` | Local database is behind | `docker compose down -v && docker compose up -d db` |
| `Could not resolve placeholder 'MISTRAL_API_KEY'` | `chiron-back/.env` missing or incomplete | `manage-env-and-secrets` |
| `Web server failed to start. Port 9090 was already in use` | A previous run is still up | Stop it before restarting |

## Tests

| Symptom | Cause | Fix |
|---------|-------|-----|
| A controller or repository test never runs in a plain `mvn test` | Surefire excludes those packages | Run `mvn verify`, or select it explicitly with `-Dtest=X`, which overrides the exclusion |
| `Could not find a valid Docker environment` | Docker not running | Start Docker |
| Testcontainers rejects the Docker API version | `-Dapi.version=1.44` lost from the Failsafe `argLine` | Restore it in `pom.xml` |
| `@WebMvcTest` returns 401 | The slice loaded the security chain | Add `excludeAutoConfiguration = SecurityAutoConfiguration.class` |
| `@WebMvcTest` cannot serialise the response | Jackson not in the slice | Add `@Import(JacksonAutoConfiguration.class)` |
| `UnnecessaryStubbingException` | A stub the code no longer reaches | Delete the stub |
| `detached entity passed to persist` | A child built before its parent was persisted | Persist the parent first |
| `LazyInitializationException` in a test | The assertion touches a lazy association outside the session | Fetch it in the query |
| Passes alone, fails in the suite | Shared mutable static state, or data persisted in `@BeforeEach` | Isolate the fixture |
| `NullPointerException` on a mocked repository | `@Mock` declared but never stubbed for that call | Stub it, or assert the interaction instead |

## Spring Boot 4 specifics

This is Boot **4.x**, not 3.x. Test-slice annotations come from the split starters, and an import
copied from a Boot 3 example will not resolve:

| Annotation | Package |
|------------|---------|
| `@WebMvcTest` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `@DataJpaTest` | `org.springframework.boot.data.jpa.test.autoconfigure` |
| `@MockitoBean` | replaces the removed `@MockBean` |

Flyway also needs the explicit `spring-boot-starter-flyway`; without it `spring.flyway.enabled: true`
is ignored and migrations never run, with no error.

## Runtime, once it starts

| Symptom | Cause |
|---------|-------|
| Endpoint answers 403 for an authenticated user | The hand-written ownership check refused, or the path is not `permitAll` and the caller is anonymous |
| Endpoint answers 500 with an opaque body | Something threw a bare `RuntimeException`; only the four mapped exceptions produce a useful body |
| Response carries unexpected nested fields | An entity leaked into the response instead of a DTO |
| Chat returns 503 | Both AI providers failed after the router's retries — `debug-ai-conversation` |
