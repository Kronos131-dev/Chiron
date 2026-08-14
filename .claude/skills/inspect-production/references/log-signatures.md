# Reading `docker logs chiron_backend`

Match the message before forming a hypothesis. Spring reports a startup failure at the **top** of the
log as a boxed `APPLICATION FAILED TO START` block; everything after it is fallout. For a restarting
container, `head -80` is the useful view, not `--tail`.

## Startup failures

| Message | Cause | Where it is fixed |
|---------|-------|-------------------|
| `Schema-validation: missing column [x] in table [y]` | An entity field shipped without a migration, and `ddl-auto: validate` refuses to start | `add-flyway-migration`, then a redeploy |
| `Schema-validation: wrong column type` | A migration changed a type in a way the entity does not match | `add-flyway-migration` |
| `Validate failed: Migrations have failed validation` / `checksum mismatch` | An already-applied migration was edited | A new `V<n>` reverting the intent; never edit the old file |
| `Detected resolved migration not applied to database` | A migration exists locally but the database is behind, or `ignore-migration-patterns` no longer covers a deleted one | Check the `flyway_schema_history` table |
| `Could not resolve placeholder 'MISTRAL_API_KEY'` | The secret is missing from `~/chiron/.env`, which the deploy rebuilds from the workflow | `manage-env-and-secrets`, then a redeploy |
| `Connection to olympus-db:5432 refused` | The Olympus stack is down, or the container is not on the `chiron-olympus` network | Check `docker ps` and `docker inspect` |
| `Web server failed to start. Port 9090 was already in use` | A stale process holds the port; the new container never took over | `ss -ltnp \| grep 9090`, then hand it to the user |

## Runtime signatures

| Message or symptom | Meaning |
|--------------------|---------|
| Everything answers 403, including `/actuator/health` | The running jar is not the deployed one — its `SecurityConfig` predates the current rules. Confirm with the sha256 comparison |
| `AiUnavailableException` in the log, 503 to the client | Both providers failed after the router's retries. Read the lines above it for the provider's own error, then apply `debug-ai-conversation` |
| Mistral or Gemini `429` / `rate limit` | Quota exhausted upstream. The router already retried and fell back |
| Gemini never appears in the log at all | `GEMINI_API_KEY` is blank on the server, so `ChironConfig` never built the Gemini agent |
| `JWT expired` on many requests at once | Expected — tokens last 30 days and expire in cohorts |
| Repeated `DEBUG o.s.security` noise | `application.yml` still ships debug logging for Spring Security, left over from a 403 investigation |
| `OutOfMemoryError` or a container restarting on its own | Check `docker stats --no-stream` and `free -m` before suspecting the code |

## Frontend and nginx

| Symptom | First check |
|---------|-------------|
| A blank page or a 404 on a deep link | `docker logs chiron_frontend` — nginx must fall back to `index.html` for client-side routes |
| The user sees an old screen, files on disk are fresh | The service worker. `pwa-update.service.ts` prompts for a reload; this is not a deploy failure |
| Static assets 404 after a deploy | Compare `ls -la ~/chiron/dist/chiron-front/browser/` against the deploy timestamp — the bundle names are hashed, so a half-synced directory serves stale HTML pointing at missing chunks |
| The API answers on `localhost:9090` but not through the domain | nginx or DNS, not the application. Read `nginx.conf` in this repository — the deploy copies it verbatim |

## Disk and memory

A full disk stops Docker writing logs and PostgreSQL writing WAL, and presents as unrelated
application errors. `df -h` costs nothing and rules it out first. Docker's own log files under
`/var/lib/docker/containers/` are the usual culprit when nothing else grew.
