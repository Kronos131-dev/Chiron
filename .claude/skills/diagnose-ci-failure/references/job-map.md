# The `Deploy Chiron` job map

`.github/workflows/deploy.yml`. Java 21 temurin, Node 22. Triggers on push to `main` and on
`workflow_dispatch`.

```
build-backend ─┐
build-frontend ┴─> test-unit ────────┐
build-android      (isolé)
                   test-integration ─┴─> deploy ─┐
                                                 │
build-olympus-backend ─┐                         │
build-olympus-frontend ┴─> test-olympus ─────────┴─> deploy-olympus
```

## Which repository each job builds

| Job | Repository | Reproduce locally |
|-----|-----------|-------------------|
| `build-backend` | **this one** | `cd chiron-back && mvn package -DskipTests --no-transfer-progress` |
| `build-frontend` | **this one** | `cd chiron-front && npm ci && npm run build -- --configuration production` |
| `build-android` | **this one** | `cd chiron-front && npm test && npm run build && npx cap sync android && cd android && ./gradlew testDebugUnitTest assembleRelease` (JDK 21, pas 25) |
| `test-unit` | **this one** | `cd chiron-back && mvn test --no-transfer-progress` |
| `test-integration` | **this one** | `cd chiron-back && mvn verify -DskipUTs=true --no-transfer-progress` (Docker required) |
| `deploy` | **this one** | not reproducible locally — it is an SSH deploy |
| `build-olympus-backend` | `Kronos131-dev/olympus`, same JDK as Chiron | not in this working tree |
| `build-olympus-frontend` | `Kronos131-dev/olympus` | not in this working tree |
| `test-olympus` | `Kronos131-dev/olympus`, against a `postgres:16-alpine` service | not in this working tree |
| `deploy-olympus` | `Kronos131-dev/olympus` | not in this working tree |

The Olympus jobs are checked out from a separate repository inside the same workflow. A stack trace
from them refers to files this working tree does not contain. `deploy-olympus` needs `deploy`, so
Chiron deploying successfully while Olympus fails is normal and leaves Chiron live.

## What a failure means for production

| Failing job | Was production touched? |
|-------------|-------------------------|
| `build-backend`, `build-frontend`, `build-android` | No |
| `test-unit`, `test-integration` | No — `deploy` needs both |
| `deploy` | **Possibly**: artefacts were uploaded and containers recreated before the gate failed |
| `test-olympus`, `build-olympus-*` | No, for either app |
| `deploy-olympus` | Chiron is live; Olympus may be half-updated |

Always say which of these applies. "The pipeline is red" and "production is broken" are different
statements.

## Inside the `deploy` job

The steps, in order: setup SSH · scp the jar · rsync the frontend · scp `docker-compose.yml` ·
scp `nginx.conf` · write the `.env` from the `printf` secrets block · run the remote deploy script ·
health check · diagnostics on failure.

The remote script stops and removes `chiron_backend`, copies the jar to both `/opt/chiron` and
`~/chiron`, rsyncs the frontend to both, ensures the `chiron-olympus` network exists, recreates the
containers, then applies two gates:

1. **sha256** — compares `/tmp/chiron-app.jar` with `/app/app.jar` inside the container. Prints
   `✓ Conteneur backend aligné sur le JAR déployé.` on success. A mismatch means another process holds
   port 9090 and the new container never took over.
2. **health check** — polls `http://46.224.227.209:9090/actuator/health` for up to 120 seconds.
   `✓ Backend UP — HTTP 200` on success. HTTP 403 means the old jar is still in memory; HTTP 000 means
   nothing is listening.

On failure the `Diagnostics backend (si échec)` step dumps `docker ps -a` and the last 200 lines of
`docker logs chiron_backend`. Read it before opening an SSH session.

## Secrets the deploy writes

`GMAIL_USERNAME`, `GMAIL_APP_PASSWORD`, `FRONTEND_URL`, `FITBIT_CLIENT_ID`, `FITBIT_CLIENT_SECRET`,
`FITBIT_REDIRECT_URI`, `OLYMPUS_DB_URL`, `OLYMPUS_DB_USERNAME`, `OLYMPUS_DB_PASSWORD`,
`VISBODY_MAILBOX_ENABLED`.

A variable the application reads but that is absent from this list never reaches the server, and the
feature degrades silently rather than failing the deploy. Apply the `manage-env-and-secrets` skill.
