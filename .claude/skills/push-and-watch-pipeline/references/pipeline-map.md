# The `Deploy Chiron` workflow

`.github/workflows/deploy.yml`. Triggers on push to `main` and on `workflow_dispatch`. Runs Java 21
(temurin) and Node 22.

## Job graph

```
build-backend ─┐
build-frontend ┴─> test-unit ────────┐
                   test-integration ─┴─> deploy ─┐
                                                 │
build-android      (isolé, ne bloque rien)       │
                                                 │
build-olympus-backend ─┐                         │
build-olympus-frontend ┴─> test-olympus ─────────┴─> deploy-olympus
```

`build-android` n'a ni dépendance ni dépendant. C'est délibéré : il construit l'APK dans le même
run pour qu'une seule page dise l'état de la livraison, mais une erreur de signature ne doit jamais
retenir un déploiement web, et le job ne reçoit aucun secret de serveur.

`deploy` needs `test-unit` and `test-integration`. `deploy-olympus` needs `deploy`,
`build-olympus-backend`, `build-olympus-frontend` and `test-olympus` — so a broken Olympus never
blocks the Chiron deploy, but a broken Chiron deploy blocks Olympus.

## What each job proves

| Job | Command | Proves |
|-----|---------|--------|
| `build-backend` | `mvn package -DskipTests` | The backend compiles; produces `chiron-back/target/app.jar` |
| `build-frontend` | `npm ci` then `npm run build -- --configuration production` | The Angular production build succeeds; produces `dist/chiron-front/browser/` |
| `test-unit` | `mvn test` | Surefire: everything outside `controller/`, `persistence/` and `migration/` |
| `test-integration` | `mvn verify -DskipUTs=true` | Failsafe: controllers, repositories, and the Flyway schema validation against a real PostgreSQL |
| `build-android` | `npm test`, `npm run build`, `npx cap sync android`, `./gradlew testDebugUnitTest` puis `assembleRelease` | Le seul job qui exécute la suite Vitest, et le seul qui compile le code Java d'Android ; produit l'APK et le publie en release quand le keystore est configuré |
| `build-olympus-*`, `test-olympus` | Build of `Kronos131-dev/olympus` on the same JDK as Chiron, against a `postgres:16-alpine` service | A repository that is **not** in this working tree |
| `deploy` | scp + rsync + `docker compose up -d --force-recreate` over SSH | The artefacts reached the server and the containers were recreated |
| `deploy-olympus` | same shape for Olympus | Olympus reached the server |

## The two gates inside `deploy`

These are the lines to look for in the job log; they are what distinguishes a real deploy from a job
that merely finished.

1. **sha256 match** — the workflow compares `sha256sum /tmp/chiron-app.jar` against
   `docker exec chiron_backend sha256sum /app/app.jar`. On success it prints
   `✓ Conteneur backend aligné sur le JAR déployé.` A mismatch means the container is running
   something else, usually because another process still holds port 9090.
2. **health check** — it polls `http://46.224.227.209:9090/actuator/health` for up to 120 seconds,
   printing `✓ Backend UP — HTTP 200` on success.

On failure the workflow runs a `Diagnostics backend (si échec)` step that dumps `docker ps -a` and the
last 200 lines of `docker logs chiron_backend`. Read that step before opening an SSH session — it
usually already holds the answer.

## Server layout the deploy assumes

| Path | Role |
|------|------|
| `/opt/chiron/app.jar`, `/opt/chiron/frontend/` | Archive copy, written with `sudo` |
| `~/chiron/app.jar`, `~/chiron/dist/chiron-front/browser/` | What `docker-compose.yml` actually mounts |
| `~/chiron/.env` | The ten workflow-managed variables are overwritten on every deploy; any other line is preserved |
| `~/chiron/docker-compose.yml`, `~/chiron/nginx.conf` | Overwritten from the repository on every deploy |
| `chiron-olympus` docker network | Shared between the two stacks; created by the `deploy` job if absent |

The deploy filters `~/chiron/.env` with `grep -v` on the ten variables it manages, then appends its
own values — so a hand-added variable outside that set survives, and a hand-edited value **inside** it
is overwritten on the next push. Anything the workflow manages belongs in the GitHub repository
secrets **and** in the `printf` block.

## Expected shape of a healthy run

The build and test jobs dominate the wall clock; `deploy` is short. If `deploy` is the long job, it is
usually stuck in the 120-second health-check loop and will fail at the end.
