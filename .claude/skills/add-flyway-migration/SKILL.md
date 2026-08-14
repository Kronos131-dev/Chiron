---
name: add-flyway-migration
description: Changes the Chiron PostgreSQL schema through a Flyway migration in chiron-back/src/main/resources/db/migration. Use when adding, renaming or dropping a column, table, index, constraint or enum value, when backfilling data, or when an entity gained a field. Covers computing the next V number, the naming pattern, the absolute rule that an applied migration is never edited, keeping JPA entities aligned under ddl-auto validate, and verifying with FlywaySchemaValidationTest against a real PostgreSQL through Testcontainers. Do not use for a JPA mapping change with no schema impact (see add-api-endpoint), for test fixtures (see write-backend-tests), or for a schema-validation failure in production (see inspect-production).
---

# Change the schema

Two rules carry all the risk here.

**An applied migration is never edited.** Flyway stores a checksum per file. Changing one that has run
makes the next startup fail validation on every environment that applied it, production included.
`.claude/hooks/check-migration-immutable.py` blocks the edit; the answer is always a new `V<n>`.

**`ddl-auto: validate`.** Hibernate compares the entities against the live schema at startup and
refuses to start on any mismatch. An entity field without a migration, or a migration without the
matching entity change, is a deployment that will not boot.

## Procedures

**Step 1: Establish what the schema must become**
1. Read the entity in `chiron-back/src/main/java/com/kronos/chiron/entity/` and decide the exact
   column name, SQL type and nullability.
2. Match the naming of the existing columns: snake_case, French domain nouns
   (`poids_corps`, `niveau_experience`, `date_naissance`).
3. Read `assets/migration-patterns.sql.md` and find the pattern for this kind of change.

**Step 2: Compute the number**
1. Run `.claude/skills/add-flyway-migration/scripts/next-migration.sh <snake_case_description>`.
2. Never count the files. V34 to V36 were deleted after being applied, so the file count and the
   highest number disagree by three.
3. Add `--create` to create the file, or create it at the printed path.

**Step 3: Write the migration**
1. Copy the matching block from `assets/migration-patterns.sql.md`.
2. Make it safe against existing rows: a new column on a populated table is either nullable or has a
   `DEFAULT`. `NOT NULL` with no default fails on the first non-empty table.
3. Backfill in the same migration when the column must end up `NOT NULL`: add nullable, `UPDATE`,
   then `ALTER … SET NOT NULL`.
4. Write nothing environment-specific. The same file runs locally and in production.
5. Do not write a rollback. Flyway Community does not run `undo`, and a file that pretends to is worse
   than none.

**Step 4: Align the entity**
1. Add or change the field in the entity, keeping the Lombok annotations of its neighbours.
2. Name the Java field in camelCase; Hibernate's default strategy maps it to the snake_case column.
   Add an explicit `@Column(name = "…")` when it does not.
3. If the column is a new enum, confirm the Java enum and the stored values agree exactly — the
   existing entities store enums as strings.
4. If the field is a collection or a relation, decide the fetch type deliberately. The entities here
   default to lazy with `@JsonIgnoreProperties` on the owning side.

**Step 5: Apply it locally**
1. Start the database: `docker compose up -d db` from the repository root.
2. Run `mvn spring-boot:run` — Flyway applies pending migrations at startup, then Hibernate validates.
3. A clean start is the proof that the migration and the entity agree.

**Step 6: Verify against a real PostgreSQL**
1. Run `mvn verify -DskipUTs=true`. `migration/FlywaySchemaValidationTest` replays every migration
   from V0 on a fresh `postgres:16-alpine` through Testcontainers, with `ddl-auto: validate` on.
2. That test is the only thing standing between a broken migration and a production deploy that
   refuses to boot. Never skip it because the application started locally — a local database that
   already holds the column passes for the wrong reason.
3. If the DTO or the API changed too, apply the `verify-backend-change` skill for the full sequence.

**Step 7: Ship it deliberately**
1. Name the migration in the commit body — the `commit-changes` skill requires it.
2. Name it again in the pre-push announcement, because it runs against production on deploy.
3. Confirm every item in `references/checklist.md`.

## Error Handling

* If the hook blocks the edit of an existing migration, that file has already run. Express the change
  as a new `V<n>` instead; to undo something, write the inverse migration.
* If the hook reports a naming problem, the file does not match `V<n>__snake_case.sql`. Flyway ignores
  anything else, so the migration would silently never run.
* If startup fails with `Schema-validation: missing column [x] in table [y]`, the entity has a field
  the schema does not. Write the migration.
* If it fails with `Schema-validation: wrong column type`, the SQL type and the Java type disagree —
  a `LocalDateTime` needs `timestamp`, a `UUID` needs `uuid`, a `boolean` needs `boolean`.
* If it fails with `Validate failed … checksum mismatch`, an applied migration was edited. Restore the
  file to its committed content; on a local database only, `docker compose down -v && docker compose up -d db`
  rebuilds from scratch.
* If it fails with `Detected resolved migration not applied to database`, the local database is behind.
  Start the application, or rebuild the volume.
* If `FlywaySchemaValidationTest` reports `Could not find a valid Docker environment`, start Docker.
* If a migration fails halfway on PostgreSQL, the whole file rolled back — PostgreSQL runs DDL
  transactionally. Fix the file and re-run; no manual cleanup is needed.
* If the column must be dropped, confirm nothing still reads it: grep the entity, the DTOs, the
  mapper, `chiron-api.ts` and the templates. A dropped column that a DTO still maps fails at startup.
