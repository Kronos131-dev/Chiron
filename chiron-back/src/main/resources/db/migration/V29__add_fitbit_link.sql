-- Liaison du compte Fitbit (OAuth2). Calque V25__add_olympus_link.sql.
-- Fitbit nécessite de stocker DEUX tokens chiffrés : l'access token (courte durée,
-- ~8 h) et le refresh token (utilisé pour renouveler l'access token sans relier).
ALTER TABLE utilisateur
    ADD COLUMN fitbit_access_token_encrypted  TEXT,
    ADD COLUMN fitbit_refresh_token_encrypted TEXT,
    ADD COLUMN fitbit_token_expires_at        TIMESTAMP,
    ADD COLUMN fitbit_user_id                 VARCHAR(64),
    ADD COLUMN fitbit_scope                   VARCHAR(255),
    ADD COLUMN fitbit_linked_at               TIMESTAMP;
