-- Restaure les colonnes de token Olympus.
--
-- L'ancienne migration V34 (« olympus_user_id ») supprimait olympus_token_encrypted
-- et olympus_token_expires_at au profit d'un id Olympus. Cette refonte a été annulée
-- côté code (l'entité Utilisateur et NutritionService utilisent de nouveau le token
-- chiffré), mais V34/35/36 avaient déjà été appliquées en production : la base de prod
-- n'a donc plus ces colonnes, et la validation de schéma Hibernate échoue au démarrage
-- (« missing column [olympus_token_encrypted] »).
--
-- Cette migration les recrée. Idempotente (IF NOT EXISTS) : sans effet sur une base
-- fraîche où V25 les a déjà créées et où elles n'ont jamais été supprimées.
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS olympus_token_encrypted TEXT;
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS olympus_token_expires_at TIMESTAMP;
