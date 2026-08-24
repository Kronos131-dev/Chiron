-- Les séances historiques créées depuis l'app Chiron (session.ts) envoyaient
-- new Date().toISOString() dans un champ typé LocalDateTime côté back. En l'absence de
-- config Jackson explicite, la désérialisation lenient de jackson-databind 3 tronque le
-- suffixe Z au lieu de convertir : l'heure UTC était stockée telle quelle, comme si elle
-- était déjà l'heure locale de Paris. Les séances créées par le coach IA (WorkoutTools,
-- LocalDateTime.now(clock)) étaient déjà justes.
--
-- Discriminant retenu entre les deux origines : toISOString() a toujours exactement trois
-- décimales de seconde, donc les microsecondes stockées sont un multiple de 1000 ;
-- LocalDateTime.now(clock) a une précision micro/nanoseconde et ne tombe quasiment jamais
-- sur la milliseconde ronde.
--
-- La conversion utilise AT TIME ZONE plutôt qu'un décalage fixe, car l'écart réel entre UTC
-- et Europe/Paris vaut une heure ou deux selon l'heure d'été/hiver au moment de la séance.
--
-- sante_activite (source CHIRON_MUSCU) est décalée de la même façon pour les activités
-- rattachées à une séance corrigée, puis les activités récentes (14 derniers jours) qui
-- n'avaient pas pu être enrichies faute de correspondance avec les mesures cardiaques sont
-- remises EN_ATTENTE pour que ActiviteEnrichissementScheduler les rattrape et que Noctua
-- produise les briefings d'activité manqués. Au-delà de 14 jours, les buckets de fréquence
-- cardiaque nécessaires ne sont plus garantis disponibles : on ne relance pas ces tentatives.

UPDATE seance
SET start_time = (start_time AT TIME ZONE 'UTC') AT TIME ZONE 'Europe/Paris',
    end_time = (end_time AT TIME ZONE 'UTC') AT TIME ZONE 'Europe/Paris'
WHERE historique = true
  AND end_time IS NOT NULL
  AND EXTRACT(MICROSECONDS FROM start_time)::int % 1000 = 0
  AND EXTRACT(MICROSECONDS FROM end_time)::int % 1000 = 0;

UPDATE sante_activite a
SET start_time = (a.start_time AT TIME ZONE 'UTC') AT TIME ZONE 'Europe/Paris',
    end_time = (a.end_time AT TIME ZONE 'UTC') AT TIME ZONE 'Europe/Paris'
FROM seance s
WHERE a.seance_id = s.id
  AND a.source = 'CHIRON_MUSCU'
  AND s.historique = true
  AND EXTRACT(MICROSECONDS FROM a.start_time)::int % 1000 = 0
  AND EXTRACT(MICROSECONDS FROM a.end_time)::int % 1000 = 0;

UPDATE sante_activite a
SET statut_enrichissement = 'EN_ATTENTE',
    tentatives_enrichissement = 0,
    prochaine_tentative_at = now()
FROM seance s
WHERE a.seance_id = s.id
  AND a.source = 'CHIRON_MUSCU'
  AND s.historique = true
  AND a.start_time >= now() - INTERVAL '14 days'
  AND (a.statut_enrichissement <> 'COMPLET' OR a.charge_cardio IS NULL);
