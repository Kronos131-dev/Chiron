
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM utilisateur WHERE username = 'Kronos') THEN
        UPDATE utilisateur SET role = 'ADMIN' WHERE username = 'Kronos';
    ELSE
        INSERT INTO utilisateur (username, password, role, is_public)
        VALUES ('Kronos', '$2a$10$hKDVYxLefS6CewvJv8kXp.hR4Vf05.T/rKk8Wl3yH/8N0d84rW41G', 'ADMIN', true);
    END IF;
END $$;


DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM utilisateur WHERE username = 'Chiron') THEN
        UPDATE utilisateur SET role = 'ADMIN' WHERE username = 'Chiron';
    ELSE
        INSERT INTO utilisateur (username, password, role, is_public)
        VALUES ('Chiron', '$2a$10$hKDVYxLefS6CewvJv8kXp.hR4Vf05.T/rKk8Wl3yH/8N0d84rW41G', 'ADMIN', true);
    END IF;
END $$;
