-- =====================================================================
-- Rend le backfill des 90 jours réessayable type par type.
--
-- ensureBackfillAsync se gardait sur « l'utilisateur a au moins une ligne
-- d'état », alors qu'une passe de synchro en écrit une pour les treize types
-- quel que soit leur résultat. Dès la première passe la garde se fermait donc
-- définitivement, et un type qui avait échoué perdait son historique pour
-- toujours : seules subsistaient les fenêtres glissantes de 3 et 7 jours, qui
-- rééchouaient à l'identique quand la cause était systémique.
--
-- Le drapeau est porté par (utilisateur, type) et ne passe à true qu'après une
-- passe réussie, si bien qu'un type resté en échec sera retenté.
-- =====================================================================

ALTER TABLE sante_sync_state
    ADD COLUMN IF NOT EXISTS backfill_termine BOOLEAN NOT NULL DEFAULT FALSE;

-- Les lignes déjà présentes décrivent des types dont l'historique n'a jamais
-- été rattrapé autrement que par la fenêtre glissante : celles qui sont en OK
-- ont bien reçu leurs 90 jours, les autres doivent repasser.
UPDATE sante_sync_state
SET backfill_termine = TRUE
WHERE dernier_statut = 'OK';
