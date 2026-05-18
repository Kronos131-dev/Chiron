CREATE TABLE chiron_memory_note (
    id              BIGSERIAL PRIMARY KEY,
    utilisateur_id  BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    type            VARCHAR(32) NOT NULL,
    content         TEXT NOT NULL,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_chiron_memory_note_user_created
    ON chiron_memory_note (utilisateur_id, created_at DESC);
