ALTER TABLE utilisateur
    ADD COLUMN date_naissance       DATE,
    ADD COLUMN sexe                 VARCHAR(16),
    ADD COLUMN taille_cm            DOUBLE PRECISION,
    ADD COLUMN niveau_experience    VARCHAR(32),
    ADD COLUMN objectif_principal   VARCHAR(32),
    ADD COLUMN frequence_visee      INTEGER,
    ADD COLUMN blessures            TEXT,
    ADD COLUMN preferences          TEXT,
    ADD COLUMN is_onboarded         BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE utilisateur_materiel (
    utilisateur_id BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    equipement     VARCHAR(32) NOT NULL,
    PRIMARY KEY (utilisateur_id, equipement)
);
