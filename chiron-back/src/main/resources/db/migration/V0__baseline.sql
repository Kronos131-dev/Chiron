-- Baseline schema : état de la base de données avant toute migration Flyway.
-- Ce script représente le schéma initial créé à l'origine par Hibernate (ddl-auto=update/create).
-- Les colonnes et tables ajoutées par V1→V5 sont ABSENTES ici (elles sont ajoutées par leurs propres scripts).

-- =====================================================================
-- TABLE : utilisateur
-- (sans icon, rank, role → ajoutés en V1 et V2)
-- =====================================================================
CREATE TABLE utilisateur (
    id        BIGSERIAL PRIMARY KEY,
    username  VARCHAR(255) NOT NULL UNIQUE,
    password  VARCHAR(255),
    is_public BOOLEAN
);

-- =====================================================================
-- TABLE : seance
-- =====================================================================
CREATE TABLE seance (
    id             BIGSERIAL PRIMARY KEY,
    titre          VARCHAR(255),
    start_time     TIMESTAMP,
    end_time       TIMESTAMP,
    week_number    INTEGER,
    is_modele      BOOLEAN NOT NULL DEFAULT FALSE,
    utilisateur_id BIGINT,
    CONSTRAINT fk_seance_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id)
);

-- =====================================================================
-- TABLE : exercice
-- =====================================================================
CREATE TABLE exercice (
    id          BIGSERIAL PRIMARY KEY,
    nom         VARCHAR(255),
    commentaire VARCHAR(255),
    start_time  TIMESTAMP,
    end_time    TIMESTAMP,
    seance_id   BIGINT,
    CONSTRAINT fk_exercice_seance FOREIGN KEY (seance_id) REFERENCES seance (id)
);

-- =====================================================================
-- TABLE : serie
-- =====================================================================
CREATE TABLE serie (
    id          BIGSERIAL PRIMARY KEY,
    poids       DOUBLE PRECISION,
    nombre_reps INTEGER,
    commentaire VARCHAR(255),
    exercice_id BIGINT,
    CONSTRAINT fk_serie_exercice FOREIGN KEY (exercice_id) REFERENCES exercice (id)
);
