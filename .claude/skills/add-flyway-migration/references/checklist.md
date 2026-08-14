# Migration checklist

## The file
* [ ] The number came from `scripts/next-migration.sh`, not from counting files.
* [ ] The name matches `V<n>__snake_case.sql`.
* [ ] No existing migration was edited.
* [ ] Column names are snake_case and match the surrounding domain vocabulary.
* [ ] `IF NOT EXISTS` / `IF EXISTS` used, as the existing migrations do.
* [ ] A French comment header explains anything the SQL does not say on its own.
* [ ] A new `NOT NULL` column has a `DEFAULT`, or is added nullable then backfilled then constrained.
* [ ] Any backfill has a `WHERE` that makes re-running harmless.
* [ ] No `DROP TABLE`, no `TRUNCATE`, nothing environment-specific.
* [ ] No rollback section — Flyway Community never runs one.

## The entity
* [ ] The entity field was added or changed in the same commit.
* [ ] The Java type matches the SQL type.
* [ ] Lombok annotations match the neighbouring fields.
* [ ] A new enum constant's stored string matches the Java constant.
* [ ] A dropped column is referenced nowhere: entity, DTO, `SeanceMapper`, `chiron-api.ts`,
      templates.

## Verification
* [ ] `docker compose up -d db` then `mvn spring-boot:run` starts cleanly.
* [ ] `mvn verify -DskipUTs=true` passes, including `FlywaySchemaValidationTest`.
* [ ] The Testcontainers run was not skipped because the local start succeeded.

## Shipping
* [ ] The migration filename is named in the commit body.
* [ ] It is named again in the pre-push announcement, because deploy runs it against production.
