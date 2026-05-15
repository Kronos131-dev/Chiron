ALTER TABLE exercice_definition
    ADD COLUMN IF NOT EXISTS usage_count BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_exercice_definition_usage_count
    ON exercice_definition (usage_count DESC);
