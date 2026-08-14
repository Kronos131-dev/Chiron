# Production inspection checklist

## Before connecting
* [ ] The `deploy` job's `Diagnostics backend (si échec)` step was read first — it often already holds
      the answer.
* [ ] `ssh -o BatchMode=yes -o ConnectTimeout=5 chiron "uptime"` succeeded, or the failure was
      reported with the exact command for the user to run.

## While reading
* [ ] Only read commands were issued; nothing on the server was mutated.
* [ ] A restarting container was read with `head`, not `--tail` — the cause is at the top.
* [ ] The symptom was matched against `references/log-signatures.md` before forming a hypothesis.
* [ ] `df -h` was checked when the application looked healthy but behaved badly.
* [ ] The right path was inspected: `~/chiron/`, which docker-compose mounts, not `/opt/chiron/`.

## Confirming which build runs
* [ ] `sha256sum /opt/chiron/app.jar` was compared with the jar inside `chiron_backend`.
* [ ] On a mismatch, `ss -ltnp | grep 9090` was checked for a stale process.
* [ ] A stale frontend was distinguished from a stale deploy by the file timestamps.

## Reporting
* [ ] What was observed, what it means, and what would fix it were stated separately.
* [ ] Any fix touching the server was written out for the user, not executed.
* [ ] For an `.env` finding, it was stated whether the variable is one of the ten the deploy
      overwrites or one that survives untouched.
* [ ] A fix that amounts to redeploying was handed to `push-and-watch-pipeline`, not improvised over
      SSH.
