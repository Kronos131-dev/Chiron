-- V58 : objectif de distance d'une sortie.
-- L'athlète annonce avant de partir la distance qu'il vise. Le temps mis à l'atteindre est
-- conservé à part de la durée totale, parce que la course continue après : il veut savoir en
-- combien il a bouclé ses dix kilomètres, même s'il en a couru douze.
ALTER TABLE course_trace ADD COLUMN objectif_distance_m DOUBLE PRECISION;
ALTER TABLE course_trace ADD COLUMN objectif_duree_s    INTEGER;
