-- V61 : un seul modèle, plus de choix de fournisseur.
-- Toute l'IA passe désormais par OpenRouter sur un modèle unique. Le fournisseur choisi par
-- l'athlète, le quota quotidien de Gemini et son compteur n'ont plus rien à décider : les
-- laisser en base ferait croire à un réglage qui n'existe plus nulle part dans le code.
ALTER TABLE utilisateur DROP CONSTRAINT IF EXISTS utilisateur_ai_provider_check;
ALTER TABLE utilisateur DROP COLUMN IF EXISTS ai_provider;
ALTER TABLE utilisateur DROP COLUMN IF EXISTS gemini_call_date;
ALTER TABLE utilisateur DROP COLUMN IF EXISTS gemini_call_count;
