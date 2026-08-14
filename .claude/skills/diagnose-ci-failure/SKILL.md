---
name: diagnose-ci-failure
description: Diagnoses a red GitHub Actions run of the Deploy Chiron workflow. Use when a run failed, when a push did not reach production, when a job passes locally but not on GitHub, or when the deploy job reported a sha256 mismatch or a failing health check. Resolves the run through the GitHub MCP server since gh is not installed, isolates the failing job, classifies it as build, unit test, integration test, deploy or health check, and gives the local reproduction command. Covers the Olympus jobs, which build a separate repository that is not in this working tree. Do not use for pushing and following a fresh run (see push-and-watch-pipeline), for a failure already reproducible locally (see verify-backend-change or verify-frontend-change), or for reading the live server (see inspect-production).
---

# Diagnose a red pipeline

Half the jobs in this workflow build `Kronos131-dev/olympus`, a **different repository** that is not
checked out here. Reading a stack trace from those jobs against this working tree wastes the whole
investigation. Establish which repository the failing job belongs to before anything else.

`gh` is not installed on this machine. Everything below goes through the GitHub MCP server.

## Procedures

**Step 1: Resolve the run**
1. Find the workflow tools with `ToolSearch` — the schemas are deferred.
2. List the recent runs of `deploy.yml` on `Kronos131-dev/Chiron` and identify the failing one by its
   head SHA, not by recency.
3. If the MCP server is unauthenticated, say so, tell the user to run `/mcp`, and stop.

**Step 2: Isolate the failing job**
1. List the run's jobs and find those with a `failure` conclusion. More than one may be red; the
   earliest in the graph is the cause and the rest are consequences.
2. Read `references/job-map.md` for the graph, what each job proves, and which repository it builds.
3. If the only red jobs are `build-olympus-backend`, `build-olympus-frontend`, `test-olympus` or
   `deploy-olympus`, the failure belongs to `Kronos131-dev/olympus`. Chiron itself deployed. Report
   that and stop unless asked to go further.

**Step 3: Read the log, not the summary**
1. Fetch the failing job's log, using the failed-step-only option when the tool offers one.
2. Find the first error, not the last. A Maven build prints a summary at the end that is a
   consequence of an error hundreds of lines earlier.
3. For `deploy`, read the `Diagnostics backend (si échec)` step: the workflow already dumped
   `docker ps -a` and the last 200 lines of `docker logs chiron_backend`.

**Step 4: Classify and reproduce locally**
1. Match the failure against `references/job-map.md` and run the exact local command it names.
2. `test-unit` reproduces with `mvn test`; `test-integration` with `mvn verify -DskipUTs=true` and a
   running Docker daemon; `build-frontend` with `npm ci && npm run build -- --configuration production`.
3. If it reproduces locally, stop using this skill — apply `verify-backend-change` or
   `verify-frontend-change`, which own the failure maps.
4. If it does **not** reproduce, read `references/ci-only-failures.md`. The usual causes are the JDK
   difference — local 25 against CI 21 — and `npm ci` versus `npm install`.

**Step 5: Handle a deploy-stage failure**
1. `deploy` failing means the tests passed and the artefacts were built; production may be in a
   half-updated state.
2. A sha256 mismatch means the container is not running the deployed jar. A health check ending on
   HTTP 403 means the old jar is still in memory; on HTTP 000, nothing is listening on port 9090.
3. Apply the `inspect-production` skill to read the server. Do not attempt to fix it over SSH — the
   guard blocks mutations and a redeploy is the pipeline's job.

**Step 6: Report and route**
1. State which job failed, which repository it belongs to, the first error, whether it reproduces
   locally, and whether production was left changed.
2. Say plainly whether the deploy ran. A failure in `test-unit` or `test-integration` means
   production was never touched.
3. Confirm every item in `references/checklist.md`.

## Error Handling

* If the MCP server returns unauthenticated, tell the user to run `/mcp`. There is no `gh` fallback
  on this machine.
* If no run exists for the commit, the workflow never triggered — confirm the push landed on `main`.
* If several jobs are red, fix the earliest in the graph first; `deploy` and `deploy-olympus` fail as
  consequences of their `needs`.
* If a Maven job fails on a formatting or compilation error that does not reproduce locally, the local
  JDK 25 accepted something CI's JDK 21 rejects.
* If `test-integration` fails on Docker, that is a runner problem only if it also passes locally with
  Docker running; otherwise it is a real Testcontainers failure.
* If `build-frontend` fails on a missing module, the lockfile and `package.json` disagree — reproduce
  with `npm ci`, never `npm install`.
* If a job was cancelled rather than failed, a later push superseded the run. Re-check against the
  newest SHA.
* If the user asks to re-run the workflow, report that it is ready to be re-run and let them trigger
  it. The guard blocks the agent from starting a production deploy.
