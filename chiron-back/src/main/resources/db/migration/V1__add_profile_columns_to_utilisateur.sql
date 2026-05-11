-- Ajout des colonnes pour le profil utilisateur
ALTER TABLE utilisateur
    ADD COLUMN icon VARCHAR(255) DEFAULT 'default_icon.png',
    ADD COLUMN rank VARCHAR(50) DEFAULT 'Citoyen';
