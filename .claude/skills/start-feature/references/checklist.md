# Starting work checklist

## Scope
* [ ] The change is stated in one sentence, with what it excludes.
* [ ] The affected side is known: back, front, or both.
* [ ] If both, the backend is being done first.
* [ ] "Done" is described as observable behaviour in the running app.
* [ ] Assumptions were stated rather than silently taken.

## Operational consequences named up front
* [ ] Whether a Flyway migration is needed.
* [ ] Whether a new environment variable is needed, and that it requires three entries.
* [ ] Whether `SecurityConfig` or the `ChironAgent` prompt changes.

## Branch
* [ ] The working tree was clean before branching.
* [ ] `git fetch` was run so the branch starts from current `main`.
* [ ] The branch is `feat/`, `fix/` or `tech/` with a kebab-case slug.
* [ ] Work is not happening directly on `main`.

## Routing
* [ ] The convention file for the affected side was read.
* [ ] `explore-codebase` was used to locate the code rather than a repository-wide grep.
* [ ] The implementation skill that owns the task was applied.

## Closing the loop
* [ ] Tests written through the matching `write-*` skill.
* [ ] Verified through the matching `verify-*` skill.
* [ ] Self-reviewed with `review-changes`.
* [ ] Committed with `commit-changes`.
* [ ] Shipped with `push-and-watch-pipeline`.
