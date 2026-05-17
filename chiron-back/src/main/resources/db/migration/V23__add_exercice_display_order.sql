-- Ordre d'affichage des exercices dans une séance (drag & drop côté Session).
-- Géré automatiquement par Hibernate via @OrderColumn sur Seance.exercices :
-- l'ordre du List<Exercice> côté Java = la valeur de display_order.

ALTER TABLE exercice ADD COLUMN display_order INTEGER;

-- Backfill : on initialise à la position courante par seance (ordre d'insertion ≈ id).
UPDATE exercice e
SET display_order = sub.rn
FROM (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY seance_id ORDER BY id) - 1 AS rn
    FROM exercice
) sub
WHERE e.id = sub.id;

-- Les exercices orphelins (seance_id NULL) ne devraient pas exister, mais on couvre le cas.
UPDATE exercice SET display_order = 0 WHERE display_order IS NULL;

ALTER TABLE exercice ALTER COLUMN display_order SET NOT NULL;
