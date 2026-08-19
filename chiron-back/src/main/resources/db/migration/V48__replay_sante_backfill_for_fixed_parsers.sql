-- =====================================================================
-- Redemande un backfill de 90 jours pour les trois types dont le parseur
-- vient d'être corrigé.
--
-- Chacun était enregistré en OK — l'appel HTTP réussissait — tout en ne
-- ramenant rien d'exploitable, si bien que V46 les a marqués
-- backfill_termine et que le rattrapage par type les ignorerait :
--
--   SLEEP                    l'agitation se lit dans shortAwakenings, un
--                            tableau qui n'était pas parcouru du tout
--   TIME_IN_HEART_RATE_ZONE  imbrication ignorée, durée au format « 47520s »
--                            illisible, et noms de zones LIGHT/MODERATE/
--                            VIGOROUS au lieu de FAT_BURN/CARDIO
--   DAILY_VO2_MAX            aucun repli quand le nœud parent ne portait
--                            pas le nom déduit du slug
--
-- Les scores de sommeil et les charges cardio sont recalculés en fin de
-- passe, donc sur toute la fenêtre reprise.
--
-- DISTANCE n'est pas de la partie : V47 a déjà corrigé son historique par
-- division, et la resynchroniser ne ferait que consommer du quota.
-- =====================================================================

UPDATE sante_sync_state
SET backfill_termine = FALSE
WHERE type_donnee IN ('SLEEP', 'TIME_IN_HEART_RATE_ZONE', 'DAILY_VO2_MAX');
