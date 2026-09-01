# Causes that produce no error

Check these before reading code. Each one makes a feature do nothing while logging nothing, so they
all present as logic bugs.

## Configuration

| Symptom | Cause | Confirm with |
|---------|-------|--------------|
| The coach answers but writes nothing to the database | The pinned OpenRouter model lost tool support, or `CHIRON_AI_MODEL` points elsewhere | `application.yml`, then `coach/configuration/ModeleIaConfig` |
| No Visbody report is ever imported | `VISBODY_MAILBOX_ENABLED` defaults to **false** | `application.yml` |
| Nutrition screens report no Olympus link | `OLYMPUS_DB_*` defaults point at the docker service name, unreachable outside the production network | the container state |
| Fitbit authorisation fails at the redirect | `FITBIT_REDIRECT_URI` must match the Google Cloud console entry exactly | the workflow `env:` block |
| Works locally, does nothing in production | The variable is read by `application.yml` but absent from the workflow `printf` block | `.github/workflows/deploy.yml` |

Full inventory in `.claude/skills/manage-env-and-secrets/references/variables.md`.

## Registration, not logic

| Symptom | Cause |
|---------|-------|
| The coach never uses a tool that exists | It is not named in brackets in the `ChironAgent` `@SystemMessage` |
| A screen shows a raw key like `journal.titre` | The key is missing from one of the two dictionaries |
| An endpoint answers 403 for everyone | Its path is not in `SecurityConfig` and the caller is anonymous |
| A route renders nothing | The component is not registered in `app.routes.ts` |
| A field never reaches the frontend | The interface in `chiron-api.ts` does not declare it, so Angular drops it |
| A migration never runs | Its filename does not match `V<n>__snake_case.sql`, so Flyway ignores it |

## Reading code that is not running

| Symptom | Cause | Confirm with |
|---------|-------|--------------|
| A fix has no effect in production | The container is not running the deployed jar | the sha256 comparison in `inspect-production` |
| A fix has no effect in the browser | The service worker is serving the previous bundle | `pwa-update.service.ts`, a hard reload |
| A repository test passes but production fails | The test runs on H2 in PostgreSQL mode, not on PostgreSQL | the `test` profile in `src/test/resources/application.yml` |
| A controller test passes but the endpoint 403s | `@WebMvcTest` excludes `SecurityAutoConfiguration`, so it never tested authorization | the annotation on the test class |

## Data shape

| Symptom | Cause |
|---------|-------|
| A boolean behaves as false for everyone | `Utilisateur.getIsPublic()` is a nullable `Boolean`; null is not false everywhere it is read |
| A mapped DTO field is null | `SeanceMapper` is hand-written and may not map it |
| A list arrives empty rather than absent | A repository returning an empty list where the caller expected `Optional` |
| A date is off by a day or a timezone | `LocalDateTime` crossing the API as an ISO string with no zone |

## Ordering and state

| Symptom | Cause |
|---------|-------|
| A test passes alone, fails in the suite | Mutable static state, or fixture data persisted in `@BeforeEach` |
| The coach loses context after a restart | Memory replay reinjects USER and AI text only, never tool calls — deliberate |
| The second conversation does not know the first | Memory is keyed by conversation id — deliberate |
