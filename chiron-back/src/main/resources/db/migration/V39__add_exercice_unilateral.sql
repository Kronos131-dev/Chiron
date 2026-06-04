-- Indique qu'un exercice a été réalisé en unilatéral (un membre à la fois, reps saisies
-- par bras/jambe). Sert au calcul du tonnage (facteur ×2 : les deux côtés sont travaillés)
-- et informe Chiron (IA) lors de l'analyse des séances.
-- Idempotent : la colonne avait existé via une ancienne V35 supprimée lors du nettoyage
-- des migrations olympus ; IF NOT EXISTS évite l'échec si elle est encore présente en prod.
ALTER TABLE exercice ADD COLUMN IF NOT EXISTS unilateral BOOLEAN NOT NULL DEFAULT false;
