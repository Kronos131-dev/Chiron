# Variable inventory

Every variable `chiron-back/src/main/resources/application.yml` reads, with what its absence does.
Extracted from the file itself, not from documentation.

`MISTRAL_API_KEY` is the **only** one with no default: the context fails to start without it.
Everything else has a default, which is why the rest fail silently.

## Mandatory

| Variable | Absent ⇒ |
|----------|----------|
| `MISTRAL_API_KEY` | `Could not resolve placeholder` — the application does not start |

## AI

| Variable | Default | Absent ⇒ |
|----------|---------|----------|
| `GEMINI_API_KEY` | blank | `ChironConfig` never builds the Gemini agent; every request silently lands on Mistral |
| `CHIRON_GEMINI_MODEL` | `gemini-3.5-flash` | the default model is used |

## Security

| Variable | Default | Absent ⇒ |
|----------|---------|----------|
| `CHIRON_SECRET_KEY` | blank | stored OAuth tokens cannot be decrypted. **Changing it invalidates every stored token** — affected users must reconnect Fitbit and Olympus |

## Frontend and mail

| Variable | Default | Absent ⇒ |
|----------|---------|----------|
| `FRONTEND_URL` | `https://chiron-sanctuaire.fr` | password-reset links point at production |
| `GMAIL_USERNAME` | blank | no outgoing mail, and no Visbody mailbox polling |
| `GMAIL_APP_PASSWORD` | blank | same |

## Fitbit, through the Google Health API

| Variable | Default | Absent ⇒ |
|----------|---------|----------|
| `FITBIT_CLIENT_ID` | blank | the OAuth flow fails at authorisation |
| `FITBIT_CLIENT_SECRET` | blank | the token exchange fails |
| `FITBIT_REDIRECT_URI` | `http://localhost:9090/api/fitbit/callback` | in production it must match the Google Cloud console entry **exactly**; it is set in the workflow `env:` block, not in the secrets, because it is not secret |
| `FITBIT_SCOPE` | the three googlehealth read scopes | the default is correct; change only alongside the console configuration |

## Olympus

| Variable | Default | Absent ⇒ |
|----------|---------|----------|
| `OLYMPUS_BASE_URL` | `http://localhost:8080` | the HTTP client points at localhost, so nutrition calls fail in production |
| `OLYMPUS_DB_URL` | `jdbc:postgresql://olympus-db:5432/olympus_db` | the direct read-only pool cannot connect |
| `OLYMPUS_DB_USERNAME` | `olympus_user` | same |
| `OLYMPUS_DB_PASSWORD` | `olympus_password` | same |
| `OLYMPUS_TOKEN_TTL_SECONDS` | `86400` | tokens last a day |

The defaults are the docker-compose service names, so Olympus works in the production network without
explicit configuration and fails locally unless the containers are up.

## Visbody

| Variable | Default | Absent ⇒ |
|----------|---------|----------|
| `VISBODY_MAILBOX_ENABLED` | `false` | **off by default** — no PDF is ever imported, with no log line saying so |
| `VISBODY_IMAP_HOST` | `imap.gmail.com` | |
| `VISBODY_IMAP_PORT` | `993` | |
| `VISBODY_IMAP_FOLDER` | `INBOX` | |
| `VISBODY_POLL_INTERVAL_MS` | `300000` | polls every five minutes |

## Storage

| Variable | Default | Absent ⇒ |
|----------|---------|----------|
| `UPLOADS_DIR` | `./uploads/images` | images are written relative to the working directory |

## What the deploy actually writes to the server

The `printf` block in the `Upload secrets` step of `.github/workflows/deploy.yml` writes exactly:

`GMAIL_USERNAME`, `GMAIL_APP_PASSWORD`, `FRONTEND_URL`, `FITBIT_CLIENT_ID`, `FITBIT_CLIENT_SECRET`,
`FITBIT_REDIRECT_URI`, `OLYMPUS_DB_URL`, `OLYMPUS_DB_USERNAME`, `OLYMPUS_DB_PASSWORD`,
`VISBODY_MAILBOX_ENABLED`.

Two consequences worth knowing:

- `MISTRAL_API_KEY`, `GEMINI_API_KEY`, `CHIRON_SECRET_KEY` and `UPLOADS_DIR` are **not** in that list.
  They persist in `~/chiron/.env` on the server from an earlier manual setup — the deploy preserves
  lines it does not overwrite by filtering with `grep -v` before appending. Do not assume a variable
  reaches production just because production works.
- Anything added to `application.yml` but not to that block never reaches the server, and the feature
  degrades silently in production while working locally.

## Local setup on a fresh machine

Create `chiron-back/.env` with at minimum:

```properties
MISTRAL_API_KEY=...
```

Add `GEMINI_API_KEY` and `CHIRON_SECRET_KEY` to exercise the provider switch and the token
encryption. Everything else defaults to something workable for local development. The file is
gitignored and must stay so.
