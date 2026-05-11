-- Ajout de la colonne role UNIQUEMENT si elle n'existe pas
-- (Dans PostgreSQL, on peut utiliser un bloc DO ou se fier à Spring Boot qui l'a déjà fait en mode "update")
DO $$
    BEGIN
        BEGIN
            ALTER TABLE utilisateur ADD COLUMN role VARCHAR(255) DEFAULT 'USER';
        EXCEPTION
            WHEN duplicate_column THEN RAISE NOTICE 'column role already exists in utilisateur.';
        END;
    END;
$$;

-- Création de la table de jointure pour les coachs
CREATE TABLE IF NOT EXISTS user_coaches (
    user_id BIGINT NOT NULL,
    coach_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, coach_id),
    FOREIGN KEY (user_id) REFERENCES utilisateur (id),
    FOREIGN KEY (coach_id) REFERENCES utilisateur (id)
);
