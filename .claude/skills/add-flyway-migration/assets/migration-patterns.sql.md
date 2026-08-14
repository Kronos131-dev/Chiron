# Migration patterns

Copy the block that matches the change. Every example is taken from
`chiron-back/src/main/resources/db/migration/`.

The project's own convention, visible across the existing files: `IF NOT EXISTS` / `IF EXISTS` almost
everywhere, and a French comment header on anything whose intent is not obvious from the SQL. The
idempotence is not decoration — V34 to V36 were deleted after having run in production, so some
environments already carry columns that a later migration re-adds.

## Add a nullable column

```sql
-- Tempo d'exécution en secondes par répétition. Optionnel : les séries
-- existantes n'en ont pas.
ALTER TABLE serie ADD COLUMN IF NOT EXISTS tempo_secondes INTEGER;
```

## Add a NOT NULL column to a populated table

Always with a `DEFAULT`, or the migration fails on the first existing row. Taken from V39:

```sql
-- Indique qu'un exercice a été réalisé en unilatéral (un membre à la fois).
-- Idempotent : la colonne avait existé via une ancienne V35 supprimée.
ALTER TABLE exercice ADD COLUMN IF NOT EXISTS unilateral BOOLEAN NOT NULL DEFAULT false;
```

## Add a NOT NULL column with no sensible default

Three statements in one file: add nullable, backfill, then constrain.

```sql
ALTER TABLE seance ADD COLUMN IF NOT EXISTS semaine_iso INTEGER;

UPDATE seance
SET semaine_iso = EXTRACT(WEEK FROM start_time)
WHERE semaine_iso IS NULL;

ALTER TABLE seance ALTER COLUMN semaine_iso SET NOT NULL;
```

## Create a table

From V27, with the foreign key, the cascade and the index it will actually be queried by:

```sql
CREATE TABLE chiron_memory_note (
    id              BIGSERIAL PRIMARY KEY,
    utilisateur_id  BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    type            VARCHAR(32) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chiron_memory_note_user_created
    ON chiron_memory_note (utilisateur_id, created_at DESC);
```

`BIGSERIAL PRIMARY KEY` matches the `Long id` the entities use. There are no UUID primary keys here.

## Add an index

From V16, including the extension it depends on:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_exercice_nom_trgm
    ON exercice USING gin (lower(nom) gin_trgm_ops);
```

Name indexes `idx_<table>_<columns>`.

## Store an enum

Enums are persisted as strings, in a `VARCHAR` wide enough for the longest constant. There is no
PostgreSQL `ENUM` type in this schema.

```sql
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS ai_provider VARCHAR(32) NOT NULL DEFAULT 'MISTRAL';
```

Adding a constant to the Java enum needs no migration. Removing or renaming one does — existing rows
still hold the old string and will fail to map.

## Rename a column

A rename is a breaking change for any code still reading the old name. Do it in one migration and
change the entity in the same commit.

```sql
ALTER TABLE utilisateur RENAME COLUMN poids TO poids_corps;
```

## Drop a column

Only once nothing reads it: grep the entity, the DTOs, `SeanceMapper`, `chiron-api.ts` and the
templates first.

```sql
ALTER TABLE exercice DROP COLUMN IF EXISTS ancien_champ;
```

## Backfill data only

```sql
-- Traductions françaises des exercices importés en anglais (cf. V24).
UPDATE exercice_definition
SET nom_fr = 'Développé couché'
WHERE nom_en = 'Bench Press' AND nom_fr IS NULL;
```

Guard every backfill with a `WHERE` that makes re-running harmless.

## Never

```sql
DROP TABLE seance;
TRUNCATE utilisateur;
```

A destructive statement in a migration runs unattended against production on the next push. If data
genuinely must go, say so and let the user do it.
