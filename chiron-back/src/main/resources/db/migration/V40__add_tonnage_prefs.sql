-- Préférences de calcul du tonnage (conventions de saisie du poids par l'utilisateur).
-- poids_haltere_par_implement : aux haltères, poids saisi = une seule haltère → tonnage ×2.
-- poids_machine_par_cote      : aux machines, poids saisi = un seul côté → tonnage ×2.
-- Idempotent : poids_machine_par_cote avait existé via une ancienne migration supprimée.
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS poids_haltere_par_implement BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE utilisateur ADD COLUMN IF NOT EXISTS poids_machine_par_cote      BOOLEAN NOT NULL DEFAULT false;
