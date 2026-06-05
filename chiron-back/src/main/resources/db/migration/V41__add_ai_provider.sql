-- Fournisseur d'IA choisi par l'utilisateur pour le coach Chiron (MISTRAL par défaut).
-- Permet de basculer le coach entre Mistral et Gemini, par utilisateur.
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS ai_provider VARCHAR(20) NOT NULL DEFAULT 'MISTRAL';

ALTER TABLE utilisateur DROP CONSTRAINT IF EXISTS utilisateur_ai_provider_check;
ALTER TABLE utilisateur ADD CONSTRAINT utilisateur_ai_provider_check
    CHECK (ai_provider IN ('MISTRAL', 'GEMINI'));
