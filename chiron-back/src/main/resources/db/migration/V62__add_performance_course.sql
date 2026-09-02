-- V62 : le Trésor accueille deux épreuves de course, le 5 km et le 10 km.
-- Une course ne se mesure ni en charge ni en répétitions : elle se mesure en secondes, et son
-- palier se lit sur la vitesse moyenne plutôt que sur un ratio au poids de corps. Les trois
-- colonnes de la barre deviennent donc facultatives, et le chronomètre s'ajoute à côté d'elles.
ALTER TABLE performance_record
    ADD COLUMN temps_secondes INTEGER;

ALTER TABLE performance_record ALTER COLUMN poids DROP NOT NULL;
ALTER TABLE performance_record ALTER COLUMN nombre_reps DROP NOT NULL;
ALTER TABLE performance_record ALTER COLUMN rm1_estime DROP NOT NULL;
