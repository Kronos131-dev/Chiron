-- =====================================================================
-- Redemande une reprise du sommeil, pour que l'historique soit renoté avec
-- le barème recalibré et débarrassé des sessions secondaires.
--
-- Deux raisons distinctes :
--
--   - le barème accordait 45 points sur 50 à la composition et à la
--     restauration réunies, là où Google n'en laisse que 32 une fois la
--     durée pleine créditée ; les seuils ont été resserrés
--   - les sessions secondaires étaient notées comme des nuits, faute d'un
--     champ « nap » qui n'existe pas dans la charge utile de Google : elles
--     se reconnaissent à mainSleep, et sont désormais écartées
--
-- Les scores ne sont recalculés qu'en fin de passe de synchro, et SLEEP est
-- repassé à backfill_termine lors de la dernière reprise réussie. Sans ce
-- drapeau remis à false, seuls les sept derniers jours seraient renotés.
-- =====================================================================

UPDATE sante_sync_state
SET backfill_termine = FALSE
WHERE type_donnee = 'SLEEP';
