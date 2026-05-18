CREATE TABLE etat_journalier (
    id              BIGSERIAL PRIMARY KEY,
    utilisateur_id  BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    date            DATE NOT NULL,
    sommeil_heures  DOUBLE PRECISION,
    fatigue         INTEGER,
    courbatures     INTEGER,
    stress          INTEGER,
    energie         INTEGER,
    notes           TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_etat_journalier_user_date UNIQUE (utilisateur_id, date),
    CONSTRAINT ck_fatigue     CHECK (fatigue     IS NULL OR (fatigue     BETWEEN 1 AND 5)),
    CONSTRAINT ck_courbatures CHECK (courbatures IS NULL OR (courbatures BETWEEN 1 AND 5)),
    CONSTRAINT ck_stress      CHECK (stress      IS NULL OR (stress      BETWEEN 1 AND 5)),
    CONSTRAINT ck_energie     CHECK (energie     IS NULL OR (energie     BETWEEN 1 AND 5))
);

CREATE INDEX idx_etat_journalier_user_date ON etat_journalier (utilisateur_id, date DESC);
