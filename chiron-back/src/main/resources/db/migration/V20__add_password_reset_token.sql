CREATE TABLE password_reset_token (
    id          BIGSERIAL PRIMARY KEY,
    token       VARCHAR(255) NOT NULL UNIQUE,
    utilisateur_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    expires_at  TIMESTAMP NOT NULL,
    used        BOOLEAN NOT NULL DEFAULT FALSE
);
