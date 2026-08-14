# Push checklist

## Before pushing to `main`
* [ ] `git log --oneline @{u}..HEAD` was read and reported to the user.
* [ ] New Flyway migrations in the range were listed by name.
* [ ] Any new environment variable exists in the GitHub repository secrets **and** in the `printf`
      block of `.github/workflows/deploy.yml`.
* [ ] Changes to `security/SecurityConfig.java` or the `ChironAgent` prompt were called out.
* [ ] The user gave an explicit go-ahead.
* [ ] The local build passed first: `mvn verify` and `npm test && npm run build`.

## Pushing
* [ ] No `--force`, `-f` or `+refspec`.
* [ ] The push landed on the intended branch.

## Watching
* [ ] The run was matched on the pushed SHA, not on recency.
* [ ] Each job's outcome was reported as it resolved.
* [ ] A failure stopped the polling and handed off to `diagnose-ci-failure`.

## After a green run
* [ ] The `deploy` log shows the sha256 alignment line.
* [ ] The `deploy` log shows the health check returning HTTP 200.
* [ ] A user-visible change was confirmed live against `https://chiron-sanctuaire.fr/`.
* [ ] Migrations that ran, and any manual follow-up, were named in the report.
* [ ] A red Olympus job was reported as belonging to a repository outside this working tree.
