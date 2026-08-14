---
name: inspect-production
description: Reads the Chiron production server at 46.224.227.209 over SSH to explain what is actually happening there. Use when the deployed application is slow, down, returning 403 or 500, serving a stale build, when the deploy reported a sha256 mismatch or a failing health check, or when container logs, disk, memory or the nginx access log need to be read. Covers verifying SSH access, the read-only command repertoire the guard permits, the container and directory layout, and reading chiron_backend, chiron_frontend, olympus-api and olympus-pwa. Do not use for a failure visible in the Actions logs (see diagnose-ci-failure), for reproducing a bug locally (see debug-systematically), or for deploying, restarting or otherwise mutating the server, which is never the agent's action.
---

# Read the production server

This server runs the only instance of Chiron there is. The agent **reads** it and never mutates it:
no `docker rm`, no `docker compose up`, no `scp` of an artefact, no `systemctl restart`.
`.claude/hooks/guard-commands.py` enforces this with an allowlist — an unrecognised remote command is
refused by default, and the refusal names the constant to extend if the command really only reads.

Before opening a session, check whether the answer is already in the workflow logs: the `deploy` job
runs a `Diagnostics backend (si échec)` step that dumps `docker ps -a` and the last 200 lines of
`docker logs chiron_backend`.

## Procedures

**Step 1: Verify access before relying on it**
1. Run `ssh -o BatchMode=yes -o ConnectTimeout=5 chiron "uptime"`.
2. If it succeeds, continue at Step 2.
3. If it fails with `Could not resolve hostname chiron`, the `~/.ssh/config` entry does not exist.
   Propose it to the user and let them add it:
   ```
   Host chiron
       HostName 46.224.227.209
       User <the deploy user>
       IdentityFile ~/.ssh/id_ed25519
   ```
4. If it fails on `Permission denied (publickey)`, the key is not authorised. Give the user the
   command and stop — do not try other keys:
   `ssh-copy-id -i ~/.ssh/id_ed25519.pub <user>@46.224.227.209`.
5. If it times out, report that the host is unreachable from here and fall back to the public probes
   in Step 5.

**Step 2: Establish what is running**
1. `ssh chiron "docker ps -a"` — the containers are `chiron_backend`, `chiron_frontend`, and, for the
   neighbouring stack, `olympus-api`, `olympus-pwa`, `olympus-db`.
2. Read the `STATUS` column. A container restarting in a loop is a startup failure, not a runtime one,
   and its cause is in the first 50 lines of its log, not the last.
3. Read `references/deployment-layout.md` for what lives where and which path docker-compose actually
   mounts.

**Step 3: Read the logs that match the symptom**
1. Backend behaviour: `ssh chiron "docker logs --tail 200 chiron_backend"`.
2. A startup failure: `ssh chiron "docker logs chiron_backend 2>&1 | head -80"` — the Spring failure
   analysis is at the top, and everything after it is noise.
3. Narrow by time rather than by size when the incident has a timestamp:
   `ssh chiron "docker logs --since 30m chiron_backend"`.
4. Static assets or routing: `ssh chiron "docker logs --tail 100 chiron_frontend"`.
5. Read `references/log-signatures.md` and match the message before forming a hypothesis.

**Step 4: Check the machine when the application looks healthy but behaves badly**
1. `ssh chiron "df -h"` — a full disk stops Docker writing logs and PostgreSQL writing WAL.
2. `ssh chiron "free -m"` and `ssh chiron "docker stats --no-stream"` — the backend holding the JVM
   heap ceiling shows up here before it shows up in the logs.
3. `ssh chiron "journalctl -u docker --since -1h --no-pager | tail -50"` for daemon-level events.

**Step 5: Probe the endpoints**
1. From the server, bypassing nginx: `ssh chiron "curl -s -o /dev/null -w '%{http_code}' localhost:9090/actuator/health"`.
2. From here, through nginx: `curl -s -o /dev/null -w '%{http_code}' https://chiron-sanctuaire.fr/`.
3. A 200 on the first and a failure on the second isolates the problem to nginx or DNS rather than the
   application.

**Step 6: Confirm which build is actually running**
1. `ssh chiron "sha256sum /opt/chiron/app.jar"` and
   `ssh chiron "docker exec chiron_backend sha256sum /app/app.jar"`.
2. A mismatch means the container is not running the deployed jar — usually a second process holding
   port 9090. Confirm with `ssh chiron "ss -ltnp | grep 9090"`.
3. For a stale frontend, compare the timestamps:
   `ssh chiron "ls -la ~/chiron/dist/chiron-front/browser/ | head"`. A user seeing an old screen with
   fresh files on disk is the service worker, not the deploy — see `pwa-update.service.ts`.

**Step 7: Report and hand back**
1. State what was observed, what it means, and what would fix it.
2. Any fix that touches the server — restarting a container, freeing disk, editing `.env` — is
   described for the user to run, never executed. When it involves `~/chiron/.env`, say whether the
   variable is one of the ten the workflow overwrites on every deploy or one that survives.
3. Confirm every item in `references/checklist.md`.

## Error Handling

* If a command is refused by the guard, it is not on the read allowlist. Reformulate it as a read, or
  hand the mutating form to the user. Do not route around the guard with `bash -c` or `sudo` — both
  are blocked for exactly that reason.
* If `ssh chiron` reports `Could not resolve hostname`, the config entry is missing. Return to Step 1.
* If `docker exec` on a container reports it is not running, that is the finding — read the log of the
  stopped container instead, which `docker logs` still serves.
* If `docker logs` returns nothing for a restarting container, the process is dying before it writes.
  Check `docker inspect <name>` for the exit code and `df -h` for a full disk.
* If the backend answers 403 on every route including `/actuator/health`, an old jar with a different
  `SecurityConfig` is in memory. Confirm with the sha256 comparison in Step 6.
* If PostgreSQL is unreachable from the backend, confirm both containers are on the expected network
  with `docker inspect` before suspecting credentials.
* If the answer is that a container must be recreated, say so and stop. Recreating it is a deploy, and
  a deploy comes from the pipeline — apply `push-and-watch-pipeline`.
