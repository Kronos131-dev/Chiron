CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_exercice_nom_trgm
    ON exercice USING gin (lower(nom) gin_trgm_ops);
