-- V55 : WOD CrossFit.
-- Type de WOD sur la définition (null = exercice de musculation classique ou cardio).
ALTER TABLE exercice_definition ADD COLUMN wod_type VARCHAR(32);

-- CINDY : AMRAP 20 minutes, un tour = 5 tractions + 10 pompes + 15 squats.
INSERT INTO exercice_definition
  (nom_fr, nom_en, muscle_principal, type_equipement, difficulte, external_id, wod_type,
   description_fr, description_en)
VALUES
  ('Cindy', 'Cindy', 'CARDIO', 'WOD', 'AVANCE', 'wod_cindy', 'CINDY',
   'AMRAP 20 minutes : réalise autant de tours que possible en 20 minutes.
Un tour = 5 tractions, 10 pompes, 15 squats.
Le score est le nombre de tours complets.',
   'AMRAP 20 minutes: complete as many rounds as possible in 20 minutes.
One round = 5 pull-ups, 10 push-ups, 15 air squats.
Your score is the number of completed rounds.');
