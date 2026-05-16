ALTER TABLE utilisateur ADD COLUMN email VARCHAR(255);
CREATE UNIQUE INDEX idx_utilisateur_email ON utilisateur(email) WHERE email IS NOT NULL;
