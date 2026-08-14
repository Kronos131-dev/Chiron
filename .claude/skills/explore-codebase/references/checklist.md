# Exploration checklist

## Method
* [ ] No search touched `target/`, `node_modules/`, `dist/`, `android/`, `.idea/` or `.git/`.
* [ ] Searches were scoped to `chiron-back/src/` or `chiron-front/src/`.
* [ ] The question was entered through the matching traversal, not a repository-wide grep.
* [ ] The call path was followed one file at a time rather than searched sideways.

## Confirming
* [ ] The file found is the one actually wired: reachable through `SecurityConfig`, `app.routes.ts`,
      or named in the `ChironAgent` prompt.
* [ ] Where two implementations exist, the live one was identified.
* [ ] A test was read where it states the rules more compactly than the code.
* [ ] `README.md` was not trusted — it documents a stack the project has outgrown.

## Reporting
* [ ] The answer is a path — what calls what — not a list of files.
* [ ] File references are given as `path:line`.
* [ ] What was **not** found, or found to be unreachable, was said plainly.
