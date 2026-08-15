# 🏛️ Chiron — Le Sanctuaire de l'Entraînement

![Java](https://img.shields.io/badge/Java-25-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.6-brightgreen.svg)
![Angular](https://img.shields.io/badge/Angular-21-dd0031.svg)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg)
![AI](https://img.shields.io/badge/AI-Mistral%20%2B%20Gemini%20via%20LangChain4j-ffb779.svg)

A strength-training tracker whose entry point is a conversation. You tell the coach
*"start a chest session"*, then *"80 kg, 8 reps"* between sets — by voice, mid-effort — and it
writes to the database while you train. Everything else in the app — the journal, the programme
builder, the statistics, the social layer — is built around that log.

The coach is not a chat bubble bolted onto a CRUD app. It reads and writes the domain through
LangChain4j function calling, so it can answer *"what was my bench PR?"* or *"what should I train
today?"* from your actual history.

---

## What it does

**The coach.** Text or voice. It opens sessions, records sets, reads your history, and adapts
advice to your recent state. Two providers are wired — Mistral and Gemini — with automatic fallback
when one is unavailable and a daily quota that silently downgrades Gemini to Mistral. Conversations
are persisted and replayed, so context survives a page reload.

**Voice logging.** The microphone uses the Web Speech API in French. An internal dictionary fixes
the mis-hearings that matter in a gym (*crêpe* → *reps*), because you are out of breath and holding
a barbell.

**Les Annales.** The journal, week by week, with training volume tracked per session.

**Programmes.** Reusable templates, with supersets (antagonist pairs) and bisets (same muscle) that
chain without rest during a session.

**Le Trésor.** For each major lift — bench, squat, deadlift, pull-ups, dips — an estimated 1RM
places you on an eight-tier scale, from Éphèbe to Olympien, computed on your strength-to-bodyweight
ratio.

**L'Agora.** Athlete profiles, public or private. You can appoint another athlete as your *Coach*:
they may then edit your programmes and see what a private profile normally hides.

**Daily state.** Mood, fatigue and soreness, crossed with the last muscle group you trained, feed
the coach's answer to *"what should I do today?"*.

**Integrations.** Nutrition from [Olympus](https://github.com/Kronos131-dev/olympus), sleep, steps
and resting heart rate from Google Health/Fitbit, body-composition reports from Visbody (PDFs
collected over IMAP) and Boditrax (CSV import).

**Clients.** Web, installable PWA, and an Android build through Capacitor.

---

## Architecture

| Folder | Stack |
|--------|-------|
| `chiron-back/` | Java 25 · Spring Boot 4.0.6 · PostgreSQL 16 / Flyway · Spring Security (stateless JWT) · LangChain4j 1.14.1 · MapStruct · springdoc OpenAPI |
| `chiron-front/` | Angular 21 standalone · Signals · Tailwind 4 · Vitest · Capacitor 8 (Android) · service worker |

### The backend is organised by business domain

There is no `controller/`, `service/` or `entity/` root package. Each module owns its full vertical
slice:

```
seance/          the core domain — Seance, Exercice, Serie, the journal
programme/       building, reordering and copying programmes
exercice/        the standardised exercise library
utilisateur/     profile, settings, ranks
auth/            registration, login, password reset
coach/           the AI subsystem
journalier/      daily state and recovery
performance/     1RM records and tiers
stats/           server-side aggregation for the statistics screen
agora/           the social listing
fitbit/ nutrition/ visbody/ boditrax/    external integrations
core/ security/  shared plumbing
```

with `controller/ dto/ model/ persistence/ service/ service/impl/` inside each.

### The AI subsystem

- `coach/agent/` — `ChironAgent` (a single LangChain4j interface carrying a long French system
  prompt), `ChironAgentRouter` (provider choice, retries, fallback), `ConversationMemoryManager`
  (memory keyed by conversation, replayed from the database).
- `coach/tools/` — the tool beans the model may call: `WorkoutTools` (the writes), plus nutrition,
  recovery, Fitbit, memory, adaptive and in-app guide tools.
- `coach/configuration/ChironConfig` — builds one `AiServices` proxy per provider over the same
  tools.

A tool only exists for the coach if it is **both** registered in `ChironConfig` **and** named in the
`ChironAgent` system prompt.

---

## Running it locally

**Prerequisites** — JDK 25, Node 22, Docker, and a Mistral API key.

```bash
# 1. Database
docker compose up -d db          # postgres:16-alpine on host port 5454

# 2. Backend configuration
cat > chiron-back/.env <<'EOF'
JWT_SECRET=<64 hex chars, e.g. openssl rand -hex 32>
MISTRAL_API_KEY=<your key>
EOF

# 3. Backend — http://localhost:9090, Swagger at /swagger-ui.html
cd chiron-back && ./mvnw spring-boot:run

# 4. Frontend — http://localhost:4200
cd chiron-front && npm install && npm start
```

`JWT_SECRET` and `MISTRAL_API_KEY` are **mandatory and have no defaults**. The application refuses
to start without them: `JwtService` rejects a secret that is absent, blank, not base64, or shorter
than 32 bytes once decoded, rather than signing tokens with a key that would be public.

Flyway builds the schema from scratch on first start (currently V0 → V44). `ddl-auto` is `validate`,
so every entity field must have a matching migration.

### Configuration

Loaded from `chiron-back/.env` through `spring.config.import`, or from the environment.

| Variable | Purpose | Required |
|----------|---------|----------|
| `JWT_SECRET` | HMAC signing key for JWTs, base64/hex, ≥ 32 bytes decoded | **yes** |
| `MISTRAL_API_KEY` | Mistral provider for the coach | **yes** |
| `GEMINI_API_KEY` | enables the Gemini provider; blank means Mistral only | no |
| `CHIRON_GEMINI_MODEL` | Gemini model name | no |
| `CHIRON_SECRET_KEY` | AES-256 key (base64) encrypting stored third-party OAuth tokens | prod |
| `GMAIL_USERNAME` / `GMAIL_APP_PASSWORD` | password-reset mail, and the Visbody mailbox | no |
| `FRONTEND_URL` | base URL used in outgoing links | no |
| `UPLOADS_DIR` | where profile images are written | no |
| `FITBIT_CLIENT_ID` / `FITBIT_CLIENT_SECRET` / `FITBIT_REDIRECT_URI` / `FITBIT_SCOPE` | Google Health OAuth2 (PKCE) | no |
| `OLYMPUS_BASE_URL` / `OLYMPUS_DB_*` / `OLYMPUS_TOKEN_TTL_SECONDS` | Olympus nutrition integration | no |
| `VISBODY_MAILBOX_ENABLED` / `VISBODY_IMAP_*` / `VISBODY_POLL_INTERVAL_MS` | IMAP polling for Visbody PDFs | no |

An integration whose variables are missing degrades silently rather than failing at startup — which
is convenient in development and easy to misread in production.

---

## Tests

```bash
cd chiron-back
mvn test                     # unit tests only — 430
mvn verify -DskipUTs=true    # integration tests only — needs a running Docker daemon
mvn verify                   # both: the hand-off gate

cd chiron-front
npm test                     # 42 specs over 11 files
```

The split is **by package name**, declared in `pom.xml`: Surefire excludes `controller/`,
`persistence/` and `migration/`, and Failsafe includes exactly those. A test's phase therefore
follows the package you file it in — a `@DataJpaTest` moved out of `persistence/` is silently
demoted to the unit phase.

Integration tests cover the controller slices, the repositories on H2 in PostgreSQL mode, and a
Testcontainers run of every Flyway migration against a real PostgreSQL, validated against the JPA
entities.

> The deploy pipeline builds the frontend but **does not run `npm test`**. A red frontend suite
> reaches `main` without turning anything red. Run it locally.

---

## Deployment

There is no staging environment. A push to `main` runs `.github/workflows/deploy.yml`, which:

1. builds the backend JAR and the production frontend bundle;
2. runs the unit and integration suites — the deploy job depends on them, so a red test never
   reaches the server;
3. checks that every required secret is present **before** touching the server, so a missing one
   fails the run while the previous container is still serving;
4. uploads the artefacts over SSH, mounts them into the containers, and restarts;
5. compares the deployed JAR's sha256 against the one running inside the container, then polls
   `/actuator/health` until it answers 200.

The same workflow also builds, tests and deploys the neighbouring
[Olympus](https://github.com/Kronos131-dev/olympus) repository — pushing to Olympus alone deploys
nothing.

---

## Conventions

Contribution rules live in [`CLAUDE.md`](CLAUDE.md) and
[`.claude/conventions/`](.claude/conventions/): no comments in production code beyond `// WHY:`
notes, DTOs as records, errors through `ErrorFactory`, the injected `Clock` instead of `now()`,
French domain nouns with English technical scaffolding. Step-by-step procedures — adding an
endpoint, a migration, an AI tool, a screen — are in [`.claude/skills/`](.claude/skills/).
