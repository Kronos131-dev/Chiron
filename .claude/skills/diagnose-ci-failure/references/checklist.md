# CI diagnosis checklist

## Resolving
* [ ] The run was matched by head SHA, not by recency.
* [ ] Every job with a `failure` conclusion was listed.
* [ ] The earliest failure in the graph was treated as the cause; later ones as consequences.
* [ ] It was established which repository the failing job builds — Chiron or `Kronos131-dev/olympus`.

## Reading
* [ ] The job log was read, not the run summary.
* [ ] The **first** error was identified, not the last.
* [ ] For a `deploy` failure, the `Diagnostics backend (si échec)` step was read first.

## Reproducing
* [ ] The local command from `references/job-map.md` was run.
* [ ] A frontend job was reproduced with `npm ci`, not `npm install`.
* [ ] A failure that reproduces locally was handed to `verify-backend-change` or
      `verify-frontend-change`.
* [ ] A failure that does not reproduce was checked against `references/ci-only-failures.md`,
      starting with the JDK 25 versus 21 difference.

## Reporting
* [ ] The report says which job failed and in which repository.
* [ ] It states plainly whether production was touched.
* [ ] A deploy-stage failure was followed into the server with `inspect-production`.
* [ ] Re-running the workflow was left to the user.
