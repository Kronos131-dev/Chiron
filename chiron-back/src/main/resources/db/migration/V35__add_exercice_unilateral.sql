-- Indique qu'un exercice a été réalisé en unilatéral (un membre à la fois, reps saisies
-- par bras). Sert au calcul du tonnage (facteur ×2 : les deux côtés sont travaillés).
ALTER TABLE exercice ADD COLUMN unilateral BOOLEAN NOT NULL DEFAULT false;
