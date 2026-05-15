CREATE TABLE exercice_definition (
    id BIGSERIAL PRIMARY KEY,
    nom_fr VARCHAR(255),
    nom_en VARCHAR(255) NOT NULL,
    description_fr TEXT,
    description_en TEXT,
    gif_path VARCHAR(512),
    muscle_principal VARCHAR(50),
    type_equipement VARCHAR(50),
    difficulte VARCHAR(20),
    external_id VARCHAR(100) UNIQUE
);

CREATE TABLE exercice_definition_muscles_secondaires (
    exercice_definition_id BIGINT NOT NULL,
    muscle VARCHAR(50) NOT NULL,
    CONSTRAINT fk_exdef_muscles FOREIGN KEY (exercice_definition_id) REFERENCES exercice_definition(id) ON DELETE CASCADE
);

CREATE INDEX idx_exdef_nom_fr ON exercice_definition(nom_fr);
CREATE INDEX idx_exdef_nom_en ON exercice_definition(nom_en);
CREATE INDEX idx_exdef_muscle ON exercice_definition(muscle_principal);
CREATE INDEX idx_exdef_equipement ON exercice_definition(type_equipement);
CREATE INDEX idx_exdef_difficulte ON exercice_definition(difficulte);
