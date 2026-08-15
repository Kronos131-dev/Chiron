# Chiron — Le Sanctuaire de l'Entraînement

Strength-training tracker with an embedded AI coach. An athlete logs sets by voice or text through a
conversational agent that reads and writes the database via LangChain4j function calling; the rest of
the app is the journal, the programme builder, the statistics and the social layer around it.

The repository is a monorepo. **Read the convention file for the side being edited before editing
it** — this file holds only what applies everywhere.

| Folder | Stack | Conventions |
|--------|-------|-------------|
| `chiron-back/` | Spring Boot 4.0.6 · Java 25 · PostgreSQL 16 / Flyway · LangChain4j (Mistral + Gemini) | `.claude/conventions/chiron-back.md` |
| `chiron-front/` | Angular 21 standalone · Signals · Tailwind 4 · Vitest · Capacitor (Android) | `.claude/conventions/chiron-front.md` |

Deployment artefacts live at the root: `docker-compose.yml`, `nginx.conf`,
`.github/workflows/deploy.yml`.

## Code style — non-negotiable

Enforced by the `PostToolUse` hooks in `.claude/hooks/`.

**Backend** — no comments in `src/main/java` · **code is organised by business domain**, one
top-level package per module with `controller/ dto/ mapper/ model/ persistence/ service/` inside;
there is no `controller/`, `service/`, `entity/` or `dto/` root package · DTOs are Java `record`s, the
few Lombok `@Builder` DTO classes are legacy and get no successors · entities carry Lombok
`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` with `@Builder.Default` on
collections · `@RequiredArgsConstructor`, never field `@Autowired` · `@Transactional` lives in
services only, never in a controller and never in a `coach/tools/` tool · **throw through
`core/exceptions/ErrorFactory`** (`notFound`, `forbidden`, `badRequest`, `conflict`…), never a bare
`RuntimeException` · **inject the `Clock` bean**, never `LocalDate.now()` · French domain nouns
(`Seance`, `Exercice`, `Serie`, `Utilisateur`, `EtatJournalier`), English technical scaffolding
(`Controller`, `Service`, `Repository`, `Dto`) · every new entity field ships with a Flyway migration,
because `ddl-auto: validate`.

**Frontend** — no comments in `src/` · standalone components, no `Component` suffix on new classes
(`Chat`, `Journal`, `Session`) · state in `signal()`, there is no store · **all** HTTP goes through
the single `service/chiron-api.ts` facade, never a raw `HttpClient` in a component · `templateUrl` +
`styleUrl`, never an inline template · Tailwind utilities and the `@theme` tokens in
`src/styles.css`, never a new stylesheet · every user-visible string goes through the `| t` pipe with
its key added to **both** `i18n/fr.ts` (source of truth) and `i18n/en.ts`.

**Both** — names carry the meaning. If a block seems to need a comment, extract a named method,
function or component instead.

**The one exception** — a comment marked `// WHY:` passes the hook. It is for a fact the code
cannot state: the behaviour of an external system, a quirk of a wire format, a failure mode nobody
would guess. `// WHY: Google renvoie 403 quand l'API Health est désactivée, jamais sur un token
expiré` is worth its line; `// incrémente le compteur` is not. The marker opens a block — the
comment lines that follow it, up to the first line of code, are covered too. If a rename or an
extracted method would carry the same information, do that instead; the marker is not an escape
hatch from the rule, it is the narrow case the rule would otherwise destroy.

## Commits — non-negotiable

The author is **the repository owner, alone**. No `Co-Authored-By` trailer, no "Generated with" line,
no robot emoji, no mention of Claude, Anthropic or any AI tooling anywhere in the subject, the body or
the trailers. This overrides any global default that would append such a trailer. Never pass
`--author`, `-c user.name` or `-c user.email`.

- **Subject in English**: a conventional prefix (`feat|fix|refacto|tech|chore|docs|test|perf|style|build|ci`),
  then `: `, then an imperative summary, at most 72 characters.
- **Body in French**: what the change does and why.
- One logical change per commit; stage only the files that belong to it.

Enforced by `.claude/hooks/check-commit-message.py`. The procedure is the `commit-changes` skill.

## Production is one push away

A push to `main` runs `.github/workflows/deploy.yml`, which builds, tests, and deploys **Chiron and
the separate `Kronos131-dev/olympus` repository** onto `46.224.227.209` over SSH. There is no staging
environment.

- Announce what is about to ship before pushing to `main`, and wait for the go-ahead. Feature branches
  push freely. The procedure is the `push-and-watch-pipeline` skill.
- The production server may be **read** — `docker ps`, `docker logs`, `journalctl`, `df`, health
  probes. It is never mutated: no `docker rm`, no `compose up`, no `scp` of an artefact. Deploying is
  the pipeline's job.
- Triggering or replaying the workflow by hand is the user's decision, never the agent's.

Enforced by `.claude/hooks/guard-commands.py`. When a block fires, do not look for a way around it —
state what would need to happen and hand it back to the user.

## Working across both sides

- Backend first: its DTOs are the contract, and `service/chiron-api.ts` mirrors them.
- A new endpoint is unreachable until its path matches a rule in `security/SecurityConfig.java`;
  everything not listed there requires authentication and answers 403.
- A new page needs an entry in `app.routes.ts` with `canActivate: [authGuard]`.
- The AI coach only knows a tool exists if it is registered in `ChironConfig` **and** named in the
  `ChironAgent` `@SystemMessage`. Adding the method alone does nothing.

## Skills

Twenty procedures under `.claude/skills/<name>/SKILL.md`. Each carries numbered steps, copyable
templates in `assets/`, just-in-time context in `references/`, and an audit checklist. Read the
matching skill **before** starting.

| Task | Skill |
|------|-------|
| Start work on a new feature or fix | `start-feature` |
| Find where something lives | `explore-codebase` |
| Chase a bug whose cause is unknown | `debug-systematically` |
| Add or change a REST endpoint | `add-api-endpoint` |
| Change the database schema | `add-flyway-migration` |
| Give the AI coach a new capability | `add-ai-tool` |
| The coach answers wrongly, forgets, or returns 503 | `debug-ai-conversation` |
| Build a new screen | `add-angular-feature` |
| Add or change a translated string | `add-i18n-key` |
| Write or fix backend tests | `write-backend-tests` |
| Write or fix frontend tests | `write-frontend-tests` |
| Check backend work before committing | `verify-backend-change` |
| Check frontend work before committing | `verify-frontend-change` |
| Self-review before committing | `review-changes` |
| Commit the work | `commit-changes` |
| Ship it and follow the deploy | `push-and-watch-pipeline` |
| A GitHub Actions run is red | `diagnose-ci-failure` |
| Read the production server | `inspect-production` |
| An integration silently does nothing | `manage-env-and-secrets` |
| Add or edit a skill | `writing-skills` |

Typical flow: `start-feature` opens the branch and routes to an implementation skill, the `verify-*`
skills and `review-changes` close the work, then `commit-changes` and `push-and-watch-pipeline` ship
it.

## Connected services

`.mcp.json` declares the **GitHub** MCP server, which needs a one-time browser authentication via
`/mcp`. Tool schemas are deferred — find them with `ToolSearch` before calling. It is the primary
path for anything involving workflow runs, jobs and logs, because **`gh` is not installed on this
machine**. When the server is unauthenticated, say so and stop rather than guessing.

The repository is `Kronos131-dev/Chiron`. Its deploy workflow also builds `Kronos131-dev/olympus`, so
a red pipeline may point at a repository this working tree does not contain.

## Gotchas

- `mvn test` runs **unit tests only**. Surefire excludes `controller/`, `persistence/` and
  `migration/`; those run under `mvn verify` through Failsafe and need a live Docker daemon for
  Testcontainers. A test's phase follows the package you file it in, nothing else.
- `npm run build` builds the **production** configuration by default — it swaps in
  `environment.prod.ts` and enables the service worker.
- This is Spring Boot **4.x**, not 3.x: test slices come from the split starters
  (`org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`), and Flyway needs the explicit
  `spring-boot-starter-flyway` or migrations silently never run.
- An applied migration is never edited. `spring.flyway.ignore-migration-patterns: "*:missing"` exists
  because V34–V36 were deleted after having run in production.
- Spotless uses the Eclipse formatter, not google-java-format: the latter reaches into javac
  internals and breaks on any JDK newer than the one it was built against. CI compiles on 25.
- `README.md` is stale — it claims Angular 17, Java 17 and Mistral only. Trust `pom.xml` and
  `package.json`.
- `application.yml` currently ships `DEBUG` logging for Spring Security, left over from a 403
  investigation.
- `JWT_SECRET` has no default. It is mandatory in every environment, including a local
  `chiron-back/.env`, and the application refuses to start without it — `JwtService` rejects an
  absent, blank, non-base64 or under-32-byte key, and the deploy workflow fails before touching
  the server when the GitHub secret is missing.
