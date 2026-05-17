-- Ordre d'affichage manuel des programmes (templates) sur la page Programme.
-- Le drag & drop côté front met à jour cette colonne via PUT /api/programmes/order.

ALTER TABLE seance ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0;

-- Backfill: on initialise l'ordre des programmes (is_modele = false) par utilisateur
-- en suivant l'ordre actuellement affiché (start_time DESC), donc 0 = en haut.
UPDATE seance s
SET display_order = sub.rn
FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY utilisateur_id ORDER BY start_time DESC) - 1 AS rn
    FROM seance
    WHERE is_modele = false
) AS sub
WHERE s.id = sub.id;
