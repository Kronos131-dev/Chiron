---
name: run-for-testing
description: Starts or restarts the Chiron backend and frontend locally so the owner can try finished work by hand before anything is committed or pushed. Use at the end of every task, once the verify skills are green, when asked to run the app, to relaunch after a change, or to get something ready to test. Covers the docker compose db on port 5454, the JDK 25 mvnw spring-boot:run on port 9090 that never hot-reloads, npm start on port 4200 that does, the actuator health probe, the narrow-viewport check, and the hand-off message that waits for an explicit go before commit-changes or push-and-watch-pipeline. Do not use for running the test suites or build gates (see verify-backend-change, verify-frontend-change), for reading the production server (see inspect-production), or for committing and shipping (see commit-changes, push-and-watch-pipeline).
---

# Hand the work over for manual testing

A task is not finished when the code compiles. It is finished when both servers run **the current
code** and the owner has been told what to try. The owner tests by hand, then says "commit" and
"push" — never before. A push to `main` is a production deploy, so a change nobody has seen running
must not reach it.

Two things make a stale server easy to hand over by mistake. The backend has no devtools: a JVM
started before the change keeps serving the old code, silently, and the owner then tests the wrong
thing. `ng serve` does rebuild on save, but only if it is running at all.

## Procedures

**Step 1: Decide what has to be (re)started**
1. Run `git status --porcelain` to see which sides changed.
2. If anything under `chiron-back/` changed, the backend must be **restarted**, even when it is
   already listening on 9090.
3. If only `chiron-front/` changed, a frontend already serving on 4200 picks the change up by
   itself. Start it only if it is down.
4. Either way, both must end up running: the frontend calls `http://localhost:9090/api`
   (`environment.ts`) and is useless without the backend.
5. If the change touched `chiron-front/android/`, no local server shows it. Say so in the hand-off:
   it needs `npx cap sync android` and an APK build, which is the owner's call.

**Step 2: Make sure the database is up**
1. Run `docker ps --format '{{.Names}} {{.Ports}}'` and look for `chiron_postgres` on `5454`.
2. If it is missing, run `docker compose up -d db` from the repository root.
3. If the change added a Flyway migration, nothing else is needed: the backend applies it on start.

**Step 3: Restart the backend**
1. Find the current listener with `ss -ltnp | grep ':9090'`. If one exists and it is a previous
   `chiron-back` run, stop that PID only.
2. Start it in the background, from `chiron-back/`, logging to a file outside the repository:
   `JAVA_HOME=/home/octav/.jdks/jdk-25.0.4+7 PATH=/home/octav/.jdks/jdk-25.0.4+7/bin:$PATH ./mvnw spring-boot:run`.
   The system `mvn` and the default `java` are too old for this project.
3. Wait for the outcome with an `until` loop on the log that stops on **both** results — the
   `Started` line and `APPLICATION FAILED`, `Caused by` or `BUILD FAILURE` — then confirm with
   `curl -s localhost:9090/actuator/health`, which answers `{"status":"UP"}`.
4. If it failed, read `references/startup-failures.md`, fix the cause, and restart. Do not hand over
   a backend that is not UP.

**Step 4: Start the frontend**
1. Check `curl -sI localhost:4200`. If it answers, leave the server alone — it already serves the
   edited files.
2. Otherwise run `npm start` in the background from `chiron-front/`, logging outside the repository,
   and wait for `Application bundle generation complete` or an `ERROR` line.
3. `npm start` uses the development configuration (`environment.ts`, no service worker). Never use
   `npm run build` output to test: it is the production configuration.

**Step 5: Look at it yourself first when it is visual**
1. A layout or styling change is not verified by a green build. Open the page when a browser is
   available, otherwise say plainly that it was not looked at.
2. Name the viewports to check: iPhone SE 375×667 and 320×568 in DevTools device mode. A real phone
   cannot reach `localhost:9090`; do not edit `environment.ts` to make it work — offer it and let the
   owner decide.

**Step 6: Hand over and stop**
1. Report in a few lines: the two URLs (`http://localhost:4200`, backend on `9090`), that both are
   running on the current code, and the exact path to click through to exercise the change — the page,
   the button, the state to reach.
2. State honestly what ran and what did not: which verify gates passed, what was not looked at,
   any Android or production-only aspect no local run can show.
3. State that nothing is committed or pushed, and wait. Do not stage, commit or announce a push
   until the owner says so. On "commit", apply `commit-changes`; on "push", apply
   `push-and-watch-pipeline`.
4. If the owner reports a problem, fix it, run the verify skills again and repeat from Step 1.

## Error Handling

* If port 9090 is held by something that is not a previous `chiron-back` run, do not kill it. Report
  the process and ask. `olympus-api` listens on 8080 and is unrelated.
* If the backend dies on `JWT_SECRET` or a missing key, apply `manage-env-and-secrets`. `.env` lives
  in `chiron-back/`, is gitignored, and the secret has no default.
* If it dies on a Flyway checksum or `Schema-validation: missing column`, a migration or an entity
  field is out of step — apply `add-flyway-migration`. Never edit an applied migration.
* If `mvnw` reports `release version 25 not supported`, `JAVA_HOME` was not set on that command.
* If `Connection refused` points at `127.0.0.1:5454`, the database container is down — Step 2.
* If port 4200 is taken by something else, `ng serve` cannot ask for another port non-interactively.
  Stop the stale `ng serve` it belongs to, or start with `npm start -- --port 4201` and give the owner
  that URL.
* If a new endpoint answers 403 in the browser, its path is missing from `SecurityConfig` — apply
  `add-api-endpoint`.
* If the browser shows an old screen, hard-reload; the service worker only exists in production
  builds, so on 4200 the cause is the browser cache.
