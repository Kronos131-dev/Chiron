# chiron-back — conventions

Spring Boot 4.0.6 · Java 21 · PostgreSQL 16 with Flyway · Spring Security with stateless JWT ·
LangChain4j 1.14.1 over Mistral and Gemini · Lombok · springdoc OpenAPI. Maven, with a wrapper
(`./mvnw`, script-only). `<finalName>app</finalName>` — the build produces `target/app.jar`.

The non-negotiable code style lives in the root `CLAUDE.md`, which is always loaded.

## Commands

Run from `chiron-back/`.

```bash
./mvnw spring-boot:run                          # http://localhost:9090, swagger at /swagger-ui.html
mvn spotless:apply                              # Eclipse formatter, bound to no phase
mvn compile
mvn test                                        # UNIT TESTS ONLY (Surefire)
mvn verify -DskipUTs=true                       # INTEGRATION TESTS ONLY (Failsafe, needs Docker)
mvn verify                                      # both — the hand-off gate
mvn -Dtest=VisbodyPdfParserTest test            # one class
mvn package -DskipTests --no-transfer-progress  # exactly what CI builds
```

Local database: `docker compose up -d db` from the repository root — `postgres:16-alpine` on host
port **5454**, `chiron_db` / `chiron_user` / `chiron_password`. The rest of `docker-compose.yml` is
the production stack and is not useful locally.

## Configuration

`spring.config.import: optional:file:.env[.properties]` loads a gitignored `chiron-back/.env`.
Variables: `JWT_SECRET` and `MISTRAL_API_KEY` (**both mandatory, no default — the application refuses
to start without them**), `GEMINI_API_KEY` (blank ⇒ the Gemini agent is never built),
`CHIRON_GEMINI_MODEL`, `CHIRON_SECRET_KEY` (base64 AES-256, encrypts stored OAuth tokens),
`GMAIL_USERNAME` / `GMAIL_APP_PASSWORD`, `FRONTEND_URL`, `UPLOADS_DIR`, `OLYMPUS_*`, `FITBIT_*`,
`VISBODY_*`. JWT expiration is 30 days. Apply the `manage-env-and-secrets` skill before adding one.

## Package map

**`com.kronos.chiron` is organised by business domain**, one top-level package per module, on the
model of `io.takima.tima.<feature>`. There is no longer a `controller/`, `service/`, `repository/`,
`entity/`, `dto/`, `mapper/`, `util/`, `config/` or `ai/` package — those were removed.

Standard layout inside a module:

```
<module>/controller/   <module>/dto/    <module>/mapper/
<module>/model/        <module>/persistence/
<module>/service/
```

Entities **and** their enums live in `model/`. Repositories live in `persistence/` and keep the
`Repository` suffix (not tima's `Dao`).

| Module | Holds |
|--------|-------|
| `seance/` | the core domain: `Seance`, `Exercice`, `Serie`, `Degressif`, `CardioType`, `ExerciseType`, the journal controller, `JournalService`, `CardioCalorieService`, `SeanceMapper` |
| `programme/` | building, reordering and copying programmes |
| `exercice/` | the exercise library: `ExerciceDefinition`, `MuscleGroup`, `TypeEquipement`, `NiveauDifficulte`, `ExerciceDataImporter` |
| `utilisateur/` | `Utilisateur` and its enums (`Role`, `Sexe`, `NiveauExperience`, `ObjectifPrincipal`, `AiProvider`), profile and settings |
| `auth/` | registration, login, password reset, `EmailService` |
| `coach/` | the AI subsystem — `agent/` (`ChironAgent`, `ChironAgentRouter`, `ConversationMemoryManager`, `AiUnavailableException`), `tools/` (the eight `@Tool` beans), `configuration/ChironConfig`, plus conversations, memory notes and the Gemini quota |
| `journalier/` | `EtatJournalier` and `RecoveryService` |
| `performance/` | `PerformanceRecord`, `PerformanceTier`, 1RM and tiers |
| `agora/` | the social listing |
| `stats/` | server-side aggregation for the statistics screen |
| `fitbit/` | OAuth2/PKCE client, sync service, parser |
| `nutrition/`, `nutrition/olympusdb/` | Olympus integration: HTTP client plus a read-only JDBC pool |
| `visbody/` | body-composition PDFs from a Gmail mailbox, parsed with PDFBox |
| `boditrax/` | CSV import |
| `core/` | `exceptions/` (`ErrorFactory`, `ChironTechnicalException`, `GlobalExceptionHandler`), `security/` (`AuthenticatedUserService`, `AdminRule`, `TokenCipherService`), `configuration/` (`CentralMapperConfig`, `OpenApiConfig`) |
| `security/` | `SecurityConfig`, `JwtService`, `JwtAuthenticationFilter`, `ApplicationConfig`, `WebMvcConfig` |

## Security model

Authorization is **URL-based**, declared in `security/SecurityConfig.java`. There is **no
`@PreAuthorize` anywhere** in this codebase — ownership, privacy and coach rules are hand-coded inside
services and inside the `ai/` tools. A new endpoint whose path is not listed as public requires
authentication and answers 403 until it is.

Public paths: `OPTIONS /**`, `/error`, `/api/auth/**`, `/api/images/**`, `/images/**`,
`/api/exercices/*/image/*`, `/actuator/health`, the swagger paths, and `GET /api/fitbit/callback`.
`POST /api/exercices/import` requires `ROLE_ADMIN`. Everything else is `authenticated()`.

Authentication is a stateless JWT (jjwt) validated by `JwtAuthenticationFilter`, placed before
`UsernamePasswordAuthenticationFilter`. CSRF is disabled; CORS carries an explicit allow-list that
includes `capacitor://localhost` for the Android build.

## Errors

**Throw through `core/exceptions/ErrorFactory`**, never a bare exception:

```java
import static com.kronos.chiron.core.exceptions.ErrorFactory.*;

throw notFound("seance", id);          // 404
throw forbidden("Accès refusé …");     // 403
throw badRequest("Le fichier est vide");
```

Each factory returns an `ErrorResponseException` carrying a `ProblemDetail`. For a technical failure
that is not the caller's fault (crypto, file I/O), throw `ChironTechnicalException` — it is annotated
`@ResponseStatus(500)`.

`core/exceptions/GlobalExceptionHandler` is a `@RestControllerAdvice` that flattens everything to
`Map.of("error", detail)`, **the shape the frontend reads** — do not change it without changing
`chiron-front`:

| Thrown | Status |
|--------|--------|
| `ErrorResponseException` (via `ErrorFactory`) | its own status |
| `MethodArgumentNotValidException` | 400, fields joined |
| `AiUnavailableException` | 503 |
| `NoSuchElementException` | 404 |
| `IllegalArgumentException` | 400 |
| `SecurityException` | 403 |

There is **no bare `RuntimeException` left in `src/main/java`**; a `grep` for it must return nothing.

## The AI subsystem

Read `.claude/skills/add-ai-tool/SKILL.md` before changing anything here.

- `ai/ChironAgent` is a single-method LangChain4j interface,
  `String chat(@MemoryId String memoryId, @UserMessage String userMessage)`, carrying a long French
  `@SystemMessage` split into rule blocks (STYLE, SÉANCE, BIBLIOTHÈQUE, NUTRITION, FITBIT, MÉMOIRE
  LONG-TERME…). Each block names the tools it may use in brackets, e.g. `[startSession]`, `[addSet]`.
  **This prompt is the main lever on coach behaviour.**
- `config/ChironConfig` builds two `AiServices` proxies of that interface — one on
  `MistralAiChatModel`, and one on `GoogleAiGeminiChatModel` only when `GEMINI_API_KEY` is non-blank.
  Both receive the *same* eight tool beans and the same `ChatMemoryProvider`.
- The tool beans are `@Component`s in `ai/`: `WorkoutTools` (41 tools, the writes), `NutritionTools`,
  `MemoryTools`, `RecoveryTools`, `AdaptiveTools`, `FitbitTools`, `AppGuideTools`,
  `AnalyseDieteTools`. A tool method is annotated `@Tool("description en français")` and takes the
  caller as `@ToolMemoryId String userId`.
- `ai/ChironAgentRouter` picks the agent from the user's `AiProvider`, retries twice on transient
  errors (503, unavailable, overloaded, timeout, deadline, 429, rate limit), resets memory before each
  retry, falls back to Mistral, and finally throws `AiUnavailableException`.
- `ai/ConversationMemoryManager` keys memory by **conversation id**, not by user —
  `MessageWindowChatMemory.withMaxMessages(20)`. Replay from the database reinjects USER and AI text
  only, never tool calls, so that an orphaned tool request cannot break the next call.
- `service/AiUsageService` caps non-admins at 5 Gemini calls a day and silently downgrades to Mistral
  past that.
- `ChatController` prepends the language directive, a `SYSTEM CONTEXT` line and the ten most recent
  `ChironMemoryNote`s to every user message.

## Database

Migrations in `src/main/resources/db/migration/`, named `V<n>__snake_case.sql`, currently V0 to V43.
`ddl-auto: validate` — an entity field without a migration fails startup. V34 to V36 were deleted
after having run in production, which is why `spring.flyway.ignore-migration-patterns: "*:missing"` is
set. **An applied migration is never edited**; a hook blocks it. Apply the `add-flyway-migration`
skill.

## Testing

The Surefire/Failsafe split is load-bearing and defined in `pom.xml`.

The split is **by package name**, in the Surefire `excludes` and Failsafe `includes` of `pom.xml`.
A test's phase therefore follows the package you put it in — moving a `@DataJpaTest` out of
`persistence/` silently demotes it to the unit phase.

| Kind | Location | Annotations | Runs under |
|------|----------|-------------|------------|
| Unit | `<module>/service/`, `<module>/model/`, `<module>/mapper/`, `coach/agent/`, `coach/tools/`, `security/`, `core/` | `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks`, AssertJ | `mvn test` |
| Controller | `<module>/controller/` | `@WebMvcTest(value = X.class, excludeAutoConfiguration = SecurityAutoConfiguration.class)`, `@Import(JacksonAutoConfiguration.class)`, `@MockitoBean`, MockMvc | `mvn verify` |
| Repository | `<module>/persistence/` | `@DataJpaTest @ActiveProfiles("test")` on H2 in PostgreSQL mode | `mvn verify` |
| Migration | `migration/` | `@Testcontainers @SpringBootTest @ActiveProfiles("schema-it")` on real PostgreSQL | `mvn verify` |

Test method names read `method_scenario_expectation`, e.g.
`getHistorique_noSessions_returnsEmptyArray`. `// Given` / `// When` / `// Then` comments are the one
place comments are allowed. Apply the `write-backend-tests` skill.

## Gotchas

- Testcontainers needs `-Dapi.version=1.44`, already set in the Failsafe `argLine`: docker-java
  defaults to API 1.32, which Docker 29 and later reject.
- Lombok is declared both as an optional dependency and in the compiler plugin's
  `annotationProcessorPaths`. Removing either breaks compilation.
- Spotless uses the Eclipse formatter with `chiron-back/eclipse-formatter.xml`, deliberately bound to
  no lifecycle phase — `.claude/hooks/format-java.sh` invokes it one file at a time. Running
  `mvn spotless:apply` with no `-DspotlessFiles` reformats the whole codebase.
- Line endings are mixed across the repository and there is no `.gitattributes`; the Spotless config
  sets `PRESERVE` so formatting never rewrites a whole file for that reason alone.
- **`username` is still passed as a query parameter on several endpoints** (`JournalController`,
  `ProgrammeController`, `AgoraController`, `ProfileController`) rather than read from the principal —
  an authenticated user can therefore read another's data. `core/security/AuthenticatedUserService`
  exists to fix this; a new endpoint must read the principal through it and never trust a `username`
  parameter.
