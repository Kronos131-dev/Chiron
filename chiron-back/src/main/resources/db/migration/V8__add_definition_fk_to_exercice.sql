ALTER TABLE exercice
    ADD COLUMN exercice_definition_id BIGINT,
    ADD CONSTRAINT fk_exercice_definition FOREIGN KEY (exercice_definition_id) REFERENCES exercice_definition(id) ON DELETE SET NULL;
