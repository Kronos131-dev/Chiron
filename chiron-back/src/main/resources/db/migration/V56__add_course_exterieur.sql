-- V56 : course en extérieur suivie au GPS.
-- Trace GPS d'une sortie. Autonome parce qu'elle est téléversée avant que la séance
-- n'existe : la page Course se termine avant l'enregistrement, et les lignes exercice
-- sont recréées à chaque sauvegarde de programme.
CREATE TABLE course_trace (
    id                 BIGSERIAL PRIMARY KEY,
    utilisateur_id     BIGINT           NOT NULL,
    points             TEXT             NOT NULL,
    nb_points          INTEGER          NOT NULL,
    distance_m         DOUBLE PRECISION NOT NULL,
    duree_s            INTEGER          NOT NULL,
    denivele_positif_m DOUBLE PRECISION NOT NULL,
    splits             TEXT,
    created_at         TIMESTAMP        NOT NULL,
    CONSTRAINT fk_course_trace_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur (id)
);

CREATE INDEX idx_course_trace_utilisateur ON course_trace (utilisateur_id, created_at DESC);

-- La série cardio pointe vers sa trace, sans association JPA : rien ne navigue de la série
-- vers la trace côté serveur, et un simple identifiant évite tout chargement paresseux.
ALTER TABLE serie ADD COLUMN course_trace_id BIGINT;
ALTER TABLE serie ADD CONSTRAINT fk_serie_course_trace
    FOREIGN KEY (course_trace_id) REFERENCES course_trace (id);

-- L'entrée 'Course' existante est le tapis : allure et pente, sans distance. Celle-ci est
-- la sortie extérieure, dont la distance est mesurée et non saisie.
INSERT INTO exercice_definition
  (nom_fr, nom_en, muscle_principal, type_equipement, difficulte, external_id, cardio_type,
   description_fr, description_en)
VALUES
  ('Course en extérieur', 'Outdoor Running', 'CARDIO', 'CARDIO', 'DEBUTANT',
   'cardio_outdoor_running', 'COURSE_EXTERIEUR',
   'Sortie suivie au GPS : distance, durée et allure sont mesurées pendant la course.
Une allure cible peut être annoncée à la voix pendant l''effort.',
   'GPS-tracked run: distance, duration and pace are measured while you run.
A target pace can be announced out loud during the effort.');
