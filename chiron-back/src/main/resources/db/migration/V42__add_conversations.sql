-- Conversations persistées du coach Chiron : chaque conversation regroupe les messages
-- échangés et alimente la mémoire de l'IA (clé mémoire = id de la conversation).
CREATE TABLE IF NOT EXISTS conversation (
    id              BIGSERIAL   PRIMARY KEY,
    utilisateur_id  BIGINT      NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    titre           VARCHAR(120),
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS conversation_message (
    id               BIGSERIAL  PRIMARY KEY,
    conversation_id  BIGINT     NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    role             VARCHAR(8) NOT NULL CHECK (role IN ('USER', 'AI')),
    content          TEXT       NOT NULL,
    created_at       TIMESTAMP  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_conversation_utilisateur
    ON conversation (utilisateur_id, updated_at DESC);

CREATE INDEX IF NOT EXISTS idx_conversation_message_conversation
    ON conversation_message (conversation_id, created_at);
