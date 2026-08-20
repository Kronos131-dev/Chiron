-- Noctua est un second agent IA, à côté de Chiron : il interprète les données santé et
-- parle à l'utilisateur de lui-même (réveil, activité, coucher). Ses conversations
-- partagent les tables conversation/conversation_message, distinguées par la colonne agent.
ALTER TABLE conversation
    ADD COLUMN agent VARCHAR(16) NOT NULL DEFAULT 'CHIRON';

CREATE TABLE noctua_briefing (
    id               BIGSERIAL   PRIMARY KEY,
    utilisateur_id   BIGINT      NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    conversation_id  BIGINT      NOT NULL REFERENCES conversation (id) ON DELETE CASCADE,
    type             VARCHAR(16) NOT NULL,
    date_reference   DATE        NOT NULL,
    cle_declencheur  VARCHAR(64) NOT NULL,
    lu               BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP   NOT NULL DEFAULT now(),
    CONSTRAINT uk_noctua_briefing_declencheur UNIQUE (utilisateur_id, cle_declencheur)
);

CREATE INDEX idx_noctua_briefing_user_created
    ON noctua_briefing (utilisateur_id, created_at DESC);
