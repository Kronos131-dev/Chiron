-- V32 : catégorie cardio.
-- Paramètres cardio par série (nullable, sans impact sur la musculation) + calories calculées.
ALTER TABLE serie ADD COLUMN duree_min   DOUBLE PRECISION;
ALTER TABLE serie ADD COLUMN distance_m  DOUBLE PRECISION;
ALTER TABLE serie ADD COLUMN allure_kmh  DOUBLE PRECISION;
ALTER TABLE serie ADD COLUMN pente_pct   DOUBLE PRECISION;
ALTER TABLE serie ADD COLUMN calories    DOUBLE PRECISION;

-- Type de cardio sur la définition (null = exercice de musculation classique).
ALTER TABLE exercice_definition ADD COLUMN cardio_type VARCHAR(32);

-- Les 4 exercices cardio (muscle_principal = CARDIO, type_equipement = CARDIO).
INSERT INTO exercice_definition
  (nom_fr, nom_en, muscle_principal, type_equipement, difficulte, external_id, cardio_type)
VALUES
  ('Marche en pente', 'Incline Walk', 'CARDIO', 'CARDIO', 'DEBUTANT',     'cardio_incline_walk', 'MARCHE_PENTE'),
  ('Course',          'Running',      'CARDIO', 'CARDIO', 'DEBUTANT',     'cardio_running',      'COURSE'),
  ('Rameur',          'Rower',        'CARDIO', 'CARDIO', 'INTERMEDIAIRE', 'cardio_rower',       'RAMEUR'),
  ('SkiErg',          'SkiErg',       'CARDIO', 'CARDIO', 'INTERMEDIAIRE', 'cardio_skierg',      'SKIERG');
