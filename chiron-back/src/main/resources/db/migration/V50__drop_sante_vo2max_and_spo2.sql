-- =====================================================================
-- Retire le VO2max, le niveau d'aptitude et la saturation en oxygène.
--
-- Le bandeau de synchro, une fois capable de distinguer « appel réussi »
-- de « données reçues », a montré que ces deux types étaient les seuls à
-- rester vides. La sonde a confirmé la cause : DAILY_VO2_MAX renvoie un
-- objet vide, pas même un tableau de points. Le bracelet ne mesure ni la
-- SpO2 ni le VO2max, et Google n'a donc rien à envoyer.
--
-- Ce n'était pas un défaut de parseur : il n'y a pas de donnée. Les
-- colonnes et l'écran Aptitude qu'elles alimentaient sont supprimés
-- plutôt que de laisser des tirets permanents.
--
-- Les lignes d'état correspondantes disparaissent aussi, sans quoi elles
-- resteraient affichées en VIDE pour des types qui ne sont plus demandés.
-- =====================================================================

ALTER TABLE sante_jour
    DROP COLUMN IF EXISTS vo2_max,
    DROP COLUMN IF EXISTS niveau_aptitude,
    DROP COLUMN IF EXISTS spo2_pct;

DELETE FROM sante_sync_state
WHERE type_donnee IN ('DAILY_VO2_MAX', 'DAILY_OXYGEN_SATURATION');
