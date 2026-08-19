-- Activité physiologique rattachée à une séance de musculation Chiron (source CHIRON_MUSCU) ou
-- détectée seule par Google Health (source GOOGLE_DETECTE : marche, course, vélo, foot). Une
-- seule table pour les deux flux : Noctua et le journal Chiron lisent depuis le même point.
-- Les colonnes calories/zones/charge_cardio restent nullables tant que la finesse de l'API
-- Google (journalière vs infra-journalière) n'est pas confirmée par sondage live.

CREATE TABLE IF NOT EXISTS sante_activite (
    id                          BIGSERIAL PRIMARY KEY,
    utilisateur_id              BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    seance_id                   BIGINT REFERENCES seance(id) ON DELETE CASCADE,
    source                      VARCHAR(32) NOT NULL,
    type_activite               VARCHAR(32) NOT NULL,
    start_time                  TIMESTAMP NOT NULL,
    end_time                    TIMESTAMP NOT NULL,
    calories                    INTEGER,
    fc_moyenne                  DOUBLE PRECISION,
    fc_min                      INTEGER,
    fc_max                      INTEGER,
    minutes_zone_basse          INTEGER,
    minutes_zone_bruleuse       INTEGER,
    minutes_zone_cardio         INTEGER,
    minutes_zone_pic            INTEGER,
    minutes_zone_active         INTEGER,
    charge_cardio               DOUBLE PRECISION,
    external_id                 VARCHAR(255),
    statut_enrichissement       VARCHAR(32) NOT NULL DEFAULT 'EN_ATTENTE',
    tentatives_enrichissement   INTEGER NOT NULL DEFAULT 0,
    prochaine_tentative_at      TIMESTAMP,
    synced_at                   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sante_activite_seance UNIQUE (seance_id),
    CONSTRAINT uk_sante_activite_user_debut_source UNIQUE (utilisateur_id, start_time, source)
);

CREATE INDEX IF NOT EXISTS idx_sante_activite_user_debut ON sante_activite (utilisateur_id, start_time DESC);

CREATE INDEX IF NOT EXISTS idx_sante_activite_relance ON sante_activite (statut_enrichissement, prochaine_tentative_at)
    WHERE statut_enrichissement = 'EN_ATTENTE';
