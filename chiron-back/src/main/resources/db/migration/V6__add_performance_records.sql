ALTER TABLE utilisateur ADD COLUMN poids_corps DOUBLE PRECISION;

CREATE TABLE performance_record (
    id BIGSERIAL PRIMARY KEY,
    utilisateur_id BIGINT NOT NULL,
    exercise_type VARCHAR(50) NOT NULL,
    poids DOUBLE PRECISION NOT NULL,
    nombre_reps INTEGER NOT NULL,
    rm1_estime DOUBLE PRECISION NOT NULL,
    ratio_performance DOUBLE PRECISION,
    poids_corporel DOUBLE PRECISION,
    recorded_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_performance_utilisateur FOREIGN KEY (utilisateur_id) REFERENCES utilisateur(id) ON DELETE CASCADE
);

CREATE INDEX idx_performance_utilisateur ON performance_record(utilisateur_id);
CREATE INDEX idx_performance_exercise ON performance_record(utilisateur_id, exercise_type);
