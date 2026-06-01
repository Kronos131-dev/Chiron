-- Préférences de saisie d'entraînement de l'utilisateur.
--  reps_par_bras           : reps saisies par bras par défaut (sinon total).
--  poids_machine_par_cote  : aux machines, le poids saisi est celui d'un seul côté
--                            (→ tonnage ×2). Sinon le poids machine est considéré total.
ALTER TABLE utilisateur
    ADD COLUMN reps_par_bras BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN poids_machine_par_cote BOOLEAN NOT NULL DEFAULT false;
