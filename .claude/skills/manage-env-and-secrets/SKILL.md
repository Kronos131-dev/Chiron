---
name: manage-env-and-secrets
description: Handles environment variables and secrets across chiron-back, the GitHub repository secrets and the deploy workflow. Use when an integration silently does nothing, when adding a configuration property or an API key, when setting up the project on a fresh machine, or when a feature works locally but not in production. Covers chiron-back/.env loaded through spring.config.import, the mandatory OPENROUTER_API_KEY and CHIRON_SECRET_KEY, the Fitbit, Olympus, Gmail and Visbody variables, and the three places a new variable must exist before it reaches the server. Do not use for a build or test failure (see verify-backend-change), for reading production logs (see inspect-production), or for a red pipeline (see diagnose-ci-failure).
---

# Environment variables and secrets

Chiron's integrations **degrade silently when unconfigured**. A `CHIRON_AI_MODEL` pointing at a
withdrawn model, or at one without tool support, leaves a coach that answers plausibly and writes
nothing. A missing Fitbit or Visbody variable means the feature does nothing. That produces symptoms
that read like logic bugs and sends debugging in the wrong direction — so when a feature "does
nothing", check the configuration before reading the code.

A new variable must exist in **three** places to reach production: `chiron-back/.env` locally, the
GitHub repository secrets, and the `printf` block of `.github/workflows/deploy.yml`. Miss the third
and the secret exists but is never written to the server.

## Procedures

**Step 1: Establish what the application actually reads**
1. Read `chiron-back/src/main/resources/application.yml`. Every variable appears there as
   `${NAME}` or `${NAME:default}`.
2. A `${NAME}` with no default is mandatory — the context fails to start without it.
   A `${NAME:}` defaulting to blank is an optional integration that will disable itself.
3. Read `references/variables.md` for the full inventory and what each one turns off when absent.

**Step 2: Diagnose a silent integration**
1. Confirm the variable is set and non-blank in the environment the code is running in.
2. Locally that is `chiron-back/.env`, loaded by `spring.config.import: optional:file:.env[.properties]`.
   The `optional:` means a missing file is not an error — the application starts and the integration
   is simply off.
3. In production it is `~/chiron/.env` on the server. The deploy overwrites the ten variables the
   workflow manages and preserves every other line, so a variable can be live in production while
   appearing nowhere in the workflow. Read it with the `inspect-production` skill rather than
   inferring it.
4. Match the symptom against `references/variables.md` before suspecting the code.

**Step 3: Add a new variable**
1. Add it to `application.yml` with an explicit default policy: no default if the application cannot
   run without it, a blank default if the feature should disable itself.
2. Add it to `chiron-back/.env` locally. That file is gitignored and must stay so.
3. Add it to the GitHub repository secrets.
4. Add it to the `printf` block of the `Upload secrets` step in `.github/workflows/deploy.yml`, in
   both the format string and the argument list — they are positional and easy to desynchronise.
5. If the variable is not secret, such as a public redirect URI, put it in the workflow `env:` block
   instead, as `FITBIT_REDIRECT_URI` already is.

**Step 4: Never commit a value**
1. No key, token or password goes into a tracked file, including `application.yml`, a test resource,
   a skill or a commit message.
2. `chiron-back/.env` is gitignored; confirm it is still ignored before staging anything.
3. If a secret was committed, stop and tell the user. Rotating the key comes before rewriting history,
   and both are the user's decision.

**Step 5: Verify**
1. Locally: start the application and confirm the integration is live, not merely that it starts.
2. In production: a variable wired through the workflow lands on the next deploy. One added by hand
   on the server persists, since the deploy only rewrites the ten it manages — but it then exists
   nowhere in version control, which is how the current `MISTRAL_API_KEY` situation arose.
3. Confirm every item in `references/checklist.md`.

## Error Handling

* If the application fails to start with `Could not resolve placeholder 'OPENROUTER_API_KEY'`, the
  mandatory key is absent. There is no default by design.
* If every AI call returns 503, read OpenRouter's own error above the exception: a 401 is a bad key,
  a 404 on the model id is a withdrawn or renamed model. See `debug-ai-conversation`.
* If token decryption fails after a redeploy, `CHIRON_SECRET_KEY` changed. Stored OAuth tokens are
  encrypted with it and cannot be recovered; the affected users must reconnect.
* If Fitbit authorisation returns a redirect-URI mismatch, `FITBIT_REDIRECT_URI` must match the
  Google Cloud console entry **exactly**. It is set in the workflow `env:` block, not in the secrets.
* If the nutrition screens report no Olympus link, check `OLYMPUS_DB_URL`, `OLYMPUS_DB_USERNAME` and
  `OLYMPUS_DB_PASSWORD`, then that the `olympus-db` container is up and on the shared network.
* If Visbody imports nothing, `VISBODY_MAILBOX_ENABLED` is false or the Gmail credentials are absent.
* If a feature works locally and not in production, the variable exists in `.env` but is missing from
  the workflow's `printf` block. That is the failure this skill exists for.
* If the `printf` block writes the wrong values, the format string and the argument list drifted out
  of alignment — they are positional.
