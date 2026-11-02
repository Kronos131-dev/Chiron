-- V65 : exports de séances de programme au format XLSX.
-- Un export est une sélection nommée : des séances de programme (modèles), chacune devenant un
-- onglet, et des séances déjà faites ajoutées à la main. Le fichier n'est pas stocké : il est
-- recalculé depuis le journal à chaque téléchargement.
CREATE TABLE export_programme (
    id             BIGSERIAL PRIMARY KEY,
    utilisateur_id BIGINT       NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
    nom            VARCHAR(120) NOT NULL,
    cree_a         TIMESTAMP    NOT NULL,
    modifie_a      TIMESTAMP    NOT NULL
);

CREATE INDEX idx_export_programme_utilisateur ON export_programme (utilisateur_id);

-- Séances du programme cochées : un onglet chacune, dans l'ordre choisi.
CREATE TABLE export_programme_modele (
    id        BIGSERIAL PRIMARY KEY,
    export_id BIGINT NOT NULL REFERENCES export_programme (id) ON DELETE CASCADE,
    modele_id BIGINT NOT NULL REFERENCES seance (id) ON DELETE CASCADE,
    ordre     INT    NOT NULL DEFAULT 0,
    CONSTRAINT uk_export_programme_modele UNIQUE (export_id, modele_id)
);
CREATE INDEX idx_export_programme_modele_modele ON export_programme_modele (modele_id);

-- Séances déjà faites ajoutées à la main, avec l'onglet (le modèle) auquel l'utilisateur les rattache.
CREATE TABLE export_programme_seance (
    id        BIGSERIAL PRIMARY KEY,
    export_id BIGINT NOT NULL REFERENCES export_programme (id) ON DELETE CASCADE,
    seance_id BIGINT NOT NULL REFERENCES seance (id) ON DELETE CASCADE,
    modele_id BIGINT NOT NULL REFERENCES seance (id) ON DELETE CASCADE,
    CONSTRAINT uk_export_programme_seance UNIQUE (export_id, seance_id)
);
CREATE INDEX idx_export_programme_seance_seance ON export_programme_seance (seance_id);
