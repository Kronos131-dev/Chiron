# When it passes locally and fails on GitHub

## The JDK difference

The local machine runs **JDK 25**; CI builds on **temurin 21**. Anything accepted by the newer
compiler and unavailable in 21 compiles here and fails there. This is the single most likely cause of
a Java job that only fails in CI.

The same difference is why Spotless uses the Eclipse formatter rather than google-java-format, which
cannot run on JDK 25 at all.

To check a suspect API, look up when it was introduced rather than guessing from the error.

## `npm ci` versus `npm install`

CI runs `npm ci`, which installs exactly what `package-lock.json` pins and fails if `package.json`
and the lockfile disagree. Local work usually runs `npm install`, which quietly reconciles them.

Reproduce a frontend job with `npm ci`. If it fails and `npm install` succeeds, the lockfile was not
committed alongside the dependency change.

## Docker and Testcontainers

`test-integration` runs `FlywaySchemaValidationTest` against a real `postgres:16-alpine` through
Testcontainers. The runner always has Docker, so a Docker failure there is a genuine test failure, not
an infrastructure one — unlike locally, where a stopped daemon is the usual cause.

The Failsafe `argLine` carries `-Dapi.version=1.44` because docker-java defaults to API 1.32, which
Docker 29 and later reject. If that is lost from `pom.xml`, it fails in both places.

## A clean checkout

CI checks out a fresh tree. Anything that works locally because of an untracked file will fail:

- `chiron-back/.env` is gitignored. Nothing in the test path may depend on it.
- `chiron-back/target/` and `chiron-front/node_modules/` do not exist on the runner.
- A file created locally but never `git add`ed is simply not there.

Run `git status` and confirm nothing needed by the build is untracked.

## The frontend test baseline

`npm test` is green locally (42 tests over 11 files) but nothing enforces that. **The workflow does not run the frontend tests at all** — there is no
`npm test` step in `build-frontend`, only `npm ci` and the production build. A broken frontend suite
therefore never turns the pipeline red, and a green pipeline says nothing about it.

## Case sensitivity

The runner is Linux, as is this machine, so a case-mismatched import fails in both. It is worth
checking only if the file was authored elsewhere.

## Ordering and flakiness

A test that passes alone and fails in the suite leaks state — a mutable static field, or fixture data
another test reads. CI runs the full suite every time, so it surfaces there first. Reproduce with a
full `mvn verify` locally, not with a single class.
