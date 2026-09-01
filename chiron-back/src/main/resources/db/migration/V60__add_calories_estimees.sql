-- V60 : distinguer une dépense mesurée d'une dépense estimée.
-- Sans compte Google Health lié, aucune fréquence cardiaque n'existe et la séance restait sans
-- calories dans le journal. Chiron les estime désormais à partir de la morphologie de l'athlète
-- et des exercices de la séance ; le drapeau dit à l'écran laquelle des deux il affiche, parce
-- qu'un chiffre calculé et un chiffre mesuré ne se lisent pas de la même façon.
ALTER TABLE sante_activite
    ADD COLUMN calories_estimees BOOLEAN NOT NULL DEFAULT FALSE;

-- Les séances déjà abandonnées ne repassent jamais devant le planificateur : sans cette
-- relance, l'historique resterait vide pour toujours. Elle ne vise que les athlètes sans compte
-- lié, chez qui la tentative échoue sans un seul appel réseau et débouche aussitôt sur
-- l'estimation ; un compte lié aurait relancé des centaines de synchronisations pour rien.
UPDATE sante_activite a
SET statut_enrichissement = 'EN_ATTENTE',
    tentatives_enrichissement = 0,
    prochaine_tentative_at = now()
FROM utilisateur u
WHERE a.utilisateur_id = u.id
  AND a.statut_enrichissement = 'ABANDONNE'
  AND a.calories IS NULL
  AND a.seance_id IS NOT NULL
  AND u.fitbit_access_token_encrypted IS NULL
  AND u.fitbit_refresh_token_encrypted IS NULL;
