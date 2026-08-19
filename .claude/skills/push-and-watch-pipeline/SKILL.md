---
name: push-and-watch-pipeline
description: Pushes Chiron work to GitHub and follows the resulting Actions run to its conclusion. Use when asked to ship, to push, to deploy, to send it to production, or to check whether the deploy went through. Covers the mandatory announcement before any push, since every push lands on main and deploys straight to the production server, then resolving the run through the GitHub MCP server, polling it, and reading the sha256 jar check and the actuator health probe the workflow performs. Do not use for diagnosing an already-red run (see diagnose-ci-failure), for reading the live server (see inspect-production), or for writing the commit itself (see commit-changes).
---

# Ship it and follow the deploy

Chiron has no staging environment and no feature branches — every push lands on `main` and runs
`.github/workflows/deploy.yml`, which builds, tests and deploys **both Chiron and the separate
`Kronos131-dev/olympus` repository** onto `46.224.227.209` over SSH. Announce before pushing and wait
for a go-ahead, every time.

`gh` is not installed on this machine. Everything below goes through the GitHub MCP server.

## Procedures

**Step 1: Establish what is about to leave**
1. Run `git status -sb` and `git log --oneline @{u}..HEAD` to list the commits about to leave.
2. If nothing is ahead of the upstream, report that and stop.

**Step 2: Announce what is about to reach production, then wait**
1. Present, in one short block: the commit subjects about to ship, any new file under
   `chiron-back/src/main/resources/db/migration/`, any new environment variable the code now reads,
   and any change to `security/SecurityConfig.java` or to the `ChironAgent` `@SystemMessage`.
2. Detect the migrations with
   `git diff --name-only @{u}..HEAD -- chiron-back/src/main/resources/db/migration/`.
3. If a new environment variable is required, confirm it exists in the GitHub repository secrets and
   in the `printf` block of `.github/workflows/deploy.yml` before pushing — a missing secret deploys
   an application that starts and then silently does nothing. Apply the `manage-env-and-secrets`
   skill.
4. Stop and wait for the user's explicit go-ahead. Do not push on an assumed yes.

**Step 3: Push**
1. Run `git push` (or `git push -u origin <branch>` for a new branch).
2. Never pass `--force`, `-f` or a `+refspec`; the guard blocks them.

**Step 4: Resolve the run**
1. Find the workflow tools with `ToolSearch` — the schemas are deferred. The relevant ones list
   workflow runs, get a run, list a run's jobs, and get a job's logs.
2. List the recent runs of `deploy.yml` on `Kronos131-dev/Chiron` and take the one whose head SHA
   matches `git rev-parse HEAD`. Matching on "most recent" picks up someone else's run.
3. If the MCP server is unauthenticated, say so, tell the user to run `/mcp`, and stop. Do not fall
   back to guessing from the commit alone.

**Step 5: Follow it to a conclusion**
1. Poll the run until its status leaves `queued` and `in_progress`. Read `references/pipeline-map.md`
   for the job graph and what each stage proves.
2. Report progress per job as it resolves rather than in one silent block at the end.
3. If any job fails, stop polling and apply the `diagnose-ci-failure` skill.

**Step 6: Confirm the deploy actually replaced the running code**
1. On success, confirm in the `deploy` job's log that both of the workflow's own gates passed: the
   sha256 comparison between `/tmp/chiron-app.jar` and `/app/app.jar` inside `chiron_backend`, and
   `/actuator/health` returning 200.
2. A green `deploy` job with those two lines present means the new jar is the one running. Anything
   else is a deploy that reported success without shipping.
3. If the change is user-visible, confirm the front is live:
   `curl -s -o /dev/null -w "%{http_code}" https://chiron-sanctuaire.fr/`.

**Step 7: Report**
1. State what shipped, which run carried it, and the outcome of each job.
2. Name anything needing a manual follow-up: a migration that ran, a secret that had to be added, the
   Olympus deploy if it was part of the run.
3. Confirm every item in `references/checklist.md`.

## Error Handling

* If the push is rejected as non-fast-forward, the remote moved. Run `git fetch` and rebase; never
  resolve it with a force push.
* If the MCP server returns unauthenticated, tell the user to run `/mcp` and authenticate `github`.
  There is no `gh` fallback on this machine.
* If no run appears for the pushed SHA after a minute, the workflow did not trigger — confirm the
  push landed on `main` and that `deploy.yml` still has `push: branches: [main]`.
* If `test-unit` or `test-integration` fails, the deploy never ran and production is untouched. Say so
  explicitly, then apply `diagnose-ci-failure`.
* If `deploy` fails on the sha256 comparison, a stale process is holding port 9090 and the container
  is not running the new jar. Apply the `inspect-production` skill.
* If the health check ends on HTTP 403, the old jar is still in memory; on HTTP 000, nothing is
  listening on 9090. Both are read from the server with `inspect-production`.
* If the Chiron jobs are green and only the Olympus jobs are red, Chiron is deployed and the failure
  belongs to `Kronos131-dev/olympus`, which is not in this working tree. Report it as such.
* If the user asks to retry the deploy, report that the workflow can be re-run and let them trigger
  it. Triggering a production deploy is not the agent's call and the guard blocks it.
