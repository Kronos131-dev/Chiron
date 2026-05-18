ALTER TABLE utilisateur
    ADD COLUMN olympus_token_encrypted TEXT,
    ADD COLUMN olympus_token_expires_at TIMESTAMP,
    ADD COLUMN olympus_username VARCHAR(255),
    ADD COLUMN olympus_linked_at TIMESTAMP;
