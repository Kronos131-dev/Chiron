-- Entrepôt de données santé (Google Health, ex-Fitbit) pour la sous-application γλαῦξ.
-- Le module fitbit/ garde l'OAuth et le transport HTTP ; ces tables sont la persistance
-- que fitbit/ n'a jamais eue (seules les heures de sommeil finissaient jusqu'ici dans
-- etat_journalier.sommeil_heures, tout le reste était recalculé à chaque requête).

CREATE TABLE IF NOT EXISTS sante_jour (
    id                        BIGSERIAL PRIMARY KEY,
    utilisateur_id            BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    date                      DATE NOT NULL,
    pas                       INTEGER,
    distance_m                DOUBLE PRECISION,
    calories_totales          INTEGER,
    calories_actives          INTEGER,
    minutes_zone_active       INTEGER,
    minutes_zone_bruleuse     INTEGER,
    minutes_zone_cardio       INTEGER,
    minutes_zone_pic          INTEGER,
    fc_repos                  INTEGER,
    fc_min                    INTEGER,
    fc_moyenne                DOUBLE PRECISION,
    fc_max                    INTEGER,
    vfc_ms                    DOUBLE PRECISION,
    vfc_sommeil_profond_ms    DOUBLE PRECISION,
    vo2_max                   DOUBLE PRECISION,
    -- Niveau Google (cardioFitnessLevel) jamais confronté à un compte réel : stocké tel
    -- quel plutôt que via un enum JPA, pour qu'une valeur inattendue ne casse pas la
    -- relecture des lignes déjà synchronisées.
    niveau_aptitude           VARCHAR(64),
    charge_cardio             DOUBLE PRECISION,
    spo2_pct                  DOUBLE PRECISION,
    frequence_respiratoire    DOUBLE PRECISION,
    synced_at                 TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sante_jour_user_date UNIQUE (utilisateur_id, date)
);

CREATE INDEX IF NOT EXISTS idx_sante_jour_user_date ON sante_jour (utilisateur_id, date DESC);

CREATE TABLE IF NOT EXISTS sante_sommeil (
    id                              BIGSERIAL PRIMARY KEY,
    utilisateur_id                  BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    date                            DATE NOT NULL,
    debut                           TIMESTAMP NOT NULL,
    fin                             TIMESTAMP,
    external_id                     VARCHAR(255),
    sieste                          BOOLEAN NOT NULL DEFAULT false,
    stades_disponibles              BOOLEAN NOT NULL DEFAULT false,
    minutes_endormi                 INTEGER,
    minutes_eveille                 INTEGER,
    minutes_avant_endormissement    INTEGER,
    minutes_apres_reveil            INTEGER,
    minutes_profond                 INTEGER,
    minutes_leger                   INTEGER,
    minutes_paradoxal               INTEGER,
    minutes_agite                   INTEGER,
    nb_reveils                      INTEGER,
    fc_sommeil_moyenne              DOUBLE PRECISION,
    score                           INTEGER,
    score_duree                     INTEGER,
    score_composition               INTEGER,
    score_restauration              INTEGER,
    synced_at                       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sante_sommeil_user_debut UNIQUE (utilisateur_id, debut)
);

CREATE INDEX IF NOT EXISTS idx_sante_sommeil_user_date ON sante_sommeil (utilisateur_id, date DESC);

-- Fréquence cardiaque en paliers de 5 minutes (rollUp Google, windowSize 300s) plutôt
-- qu'en échantillon brut : ~288 lignes/jour au lieu des ~8700 que l'API peut renvoyer
-- en liste, pour une courbe intrajournalière fidèle sans faire exploser la table.
CREATE TABLE IF NOT EXISTS sante_frequence_cardiaque (
    id                BIGSERIAL PRIMARY KEY,
    utilisateur_id    BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    horodatage        TIMESTAMP NOT NULL,
    fc_min            INTEGER,
    fc_moyenne        DOUBLE PRECISION,
    fc_max            INTEGER,
    nb_echantillons   INTEGER,
    synced_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sante_fc_user_horodatage UNIQUE (utilisateur_id, horodatage)
);

CREATE INDEX IF NOT EXISTS idx_sante_fc_user_horodatage ON sante_frequence_cardiaque (utilisateur_id, horodatage DESC);

-- Un type de donnée Google Health (steps, sleep, heart-rate…) peut être synchronisé, en
-- panne ou non autorisé indépendamment des autres : chacun a sa ligne, plutôt qu'un statut
-- global qui masquerait un blocage partiel.
CREATE TABLE IF NOT EXISTS sante_sync_state (
    id                             BIGSERIAL PRIMARY KEY,
    utilisateur_id                 BIGINT NOT NULL REFERENCES utilisateur(id) ON DELETE CASCADE,
    type_donnee                    VARCHAR(64) NOT NULL,
    derniere_date_synchronisee     DATE,
    derniere_execution             TIMESTAMP,
    dernier_statut                 VARCHAR(32) NOT NULL DEFAULT 'INDISPONIBLE',
    dernier_message                VARCHAR(500),
    CONSTRAINT uk_sante_sync_state_user_type UNIQUE (utilisateur_id, type_donnee)
);
