# Environment checklist

## Diagnosing a silent feature
* [ ] `application.yml` was read to find the variable's real name and default.
* [ ] It was confirmed set and non-blank in the environment where the code actually runs.
* [ ] `references/variables.md` was checked before suspecting the code.
* [ ] For production, `~/chiron/.env` was read on the server, not assumed.

## Adding a variable
* [ ] Added to `application.yml` with a deliberate default policy — none if mandatory, blank if the
      feature should disable itself.
* [ ] Added to `chiron-back/.env` locally.
* [ ] Added to the GitHub repository secrets.
* [ ] Added to the `printf` block of the `Upload secrets` step, in **both** the format string and the
      positional argument list.
* [ ] A non-secret value went into the workflow `env:` block instead, as `FITBIT_REDIRECT_URI` does.

## Safety
* [ ] No value was written into a tracked file — not `application.yml`, not a test resource, not a
      commit message, not a skill.
* [ ] `chiron-back/.env` is still gitignored and was not staged.
* [ ] A committed secret was reported to the user rather than quietly rewritten.
* [ ] `CHIRON_SECRET_KEY` was not changed without saying that every stored OAuth token becomes
      unreadable.

## Verification
* [ ] Locally, the integration was confirmed **working**, not merely that the application started.
* [ ] It was stated whether the variable is workflow-managed — landing on the next deploy — or lives
      only in `~/chiron/.env` on the server, outside version control.
