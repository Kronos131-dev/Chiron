# What lives where on the production server

Host `46.224.227.209`, reached as `chiron` once the `~/.ssh/config` entry exists. Everything below is
derived from `.github/workflows/deploy.yml`, `docker-compose.yml` and `nginx.conf` in this repository.

## Containers

| Container | Serves | Notes |
|-----------|--------|-------|
| `chiron_backend` | Spring Boot on port 9090 | Runs `/app/app.jar`, mounted from the host |
| `chiron_frontend` | nginx serving the Angular build | Mounts the browser bundle read-only |
| `olympus-api` | the neighbouring Olympus app | Runs `/app/olympus.jar`, Java 17 |
| `olympus-pwa` | the Olympus front, port 8082 under `/olympus` | |
| `olympus-db` | PostgreSQL for Olympus | |

They share the `chiron-olympus` docker network, created by the `deploy` job if it does not exist.
Chiron reads the Olympus database directly through a second JDBC pool, so a stopped `olympus-db`
surfaces as a nutrition failure inside Chiron.

## Directories

| Path | Role |
|------|------|
| `/opt/chiron/app.jar`, `/opt/chiron/frontend/` | Archive copy written with `sudo` by the deploy |
| `~/chiron/app.jar` | **The jar docker-compose actually mounts** |
| `~/chiron/dist/chiron-front/browser/` | **The bundle nginx actually serves** |
| `~/chiron/.env` | The ten workflow-managed variables are overwritten on every deploy; other lines are preserved |
| `~/chiron/docker-compose.yml`, `~/chiron/nginx.conf` | Overwritten from the repository on every deploy |
| `~/olympus/` | The same shape for Olympus |

The two-location split is the single most common source of confusion: updating `/opt/chiron` alone
changes nothing, because the containers mount `~/chiron`. The deploy writes both.

The deploy filters `~/chiron/.env` with `grep -v` on the ten variables the workflow manages, then
appends its own values. A variable outside that set — `MISTRAL_API_KEY`, `GEMINI_API_KEY`,
`CHIRON_SECRET_KEY` — survives from the original manual setup, which is why production works despite
those never appearing in the workflow. A hand-edited value **inside** the managed set is overwritten
on the next push.

## Read-only repertoire the guard permits

```bash
ssh chiron "docker ps -a"
ssh chiron "docker logs --tail 200 chiron_backend"
ssh chiron "docker logs --since 30m chiron_backend"
ssh chiron "docker logs chiron_backend 2>&1 | head -80"     # startup failures live at the top
ssh chiron "docker stats --no-stream"
ssh chiron "docker inspect chiron_backend"
ssh chiron "docker exec chiron_backend sha256sum /app/app.jar"
ssh chiron "df -h"
ssh chiron "free -m"
ssh chiron "journalctl -u docker --since -1h --no-pager | tail -50"
ssh chiron "ss -ltnp | grep 9090"
ssh chiron "curl -s -o /dev/null -w '%{http_code}' localhost:9090/actuator/health"
ssh chiron "ls -la ~/chiron/dist/chiron-front/browser/ | head"
ssh chiron "sha256sum /opt/chiron/app.jar"
```

`docker exec` is permitted only when the command it runs is itself a read — `cat`, `sha256sum`,
`ls`, `tail`. `docker exec … bash` is refused, because an interactive shell is a blank cheque.

## Refused, and who does it instead

| Action | Who |
|--------|-----|
| `docker rm`, `docker stop`, `docker restart`, `docker compose up`, `docker compose down` | The user, or the pipeline |
| `scp` / `rsync` of a jar or a bundle onto the server | The `deploy` job |
| Editing `~/chiron/.env` | The user, knowing the next deploy overwrites it |
| `sudo` anything | The user |
| Re-running the deploy workflow | The user |

Describe the command for the user rather than searching for a permitted spelling of it.
