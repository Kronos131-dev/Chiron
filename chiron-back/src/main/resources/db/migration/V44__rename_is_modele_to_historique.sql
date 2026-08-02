-- Le nom is_modele disait l'inverse de ce que la colonne signifie : true = séance
-- réellement effectuée (historique), false = modèle de programme. Voir V4, qui
-- backfillait is_modele = true pour tout l'historique.
--
-- Renommage sans inversion : la polarité reste identique (true = historique),
-- seul le nom change. Aucune donnée n'est touchée.
ALTER TABLE seance RENAME COLUMN is_modele TO historique;
