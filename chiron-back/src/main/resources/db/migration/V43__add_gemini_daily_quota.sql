-- Quota journalier Gemini : les utilisateurs non-admin sont limités à 5 requêtes Gemini/jour.
-- Au-delà, le coach bascule silencieusement sur Mistral. Le compteur est remis à zéro chaque jour.
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS gemini_call_date  DATE;
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS gemini_call_count INT NOT NULL DEFAULT 0;
