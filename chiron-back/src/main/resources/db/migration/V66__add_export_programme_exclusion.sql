-- V66 : séances écartées d'un export.
-- Une séance faite entre seule dans un export quand son modèle d'origine ou son titre correspond à
-- l'une des séances choisies. Pour qu'on puisse l'en retirer, l'écart est mémorisé : le fichier est
-- recalculé à chaque téléchargement et la règle d'appartenance le ferait sinon revenir.
CREATE TABLE export_programme_exclusion (
    id        BIGSERIAL PRIMARY KEY,
    export_id BIGINT NOT NULL REFERENCES export_programme (id) ON DELETE CASCADE,
    seance_id BIGINT NOT NULL REFERENCES seance (id) ON DELETE CASCADE,
    CONSTRAINT uk_export_programme_exclusion UNIQUE (export_id, seance_id)
);
CREATE INDEX idx_export_programme_exclusion_seance ON export_programme_exclusion (seance_id);
