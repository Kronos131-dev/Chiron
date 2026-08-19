-- =====================================================================
-- Corrige l'unité des distances déjà synchronisées.
--
-- Google Health renvoie la distance en millimètres, alors que la colonne
-- distance_m et tout ce qui la consomme attendent des mètres. Les lignes
-- existantes portent donc une valeur mille fois trop grande : l'écran
-- affichait 6175,60 km pour 8623 pas, soit 716 millimètres par pas.
--
-- La synchro corrige désormais l'unité à l'écriture, mais elle ne réécrit
-- que sa fenêtre glissante, et DISTANCE est marquée backfill_termine :
-- l'historique ne serait jamais repris sans ce UPDATE.
-- =====================================================================

UPDATE sante_jour
SET distance_m = distance_m / 1000.0
WHERE distance_m IS NOT NULL;
