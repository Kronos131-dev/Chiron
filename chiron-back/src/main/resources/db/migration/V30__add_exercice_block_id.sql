-- Groupage des exercices d'une séance en supersets/bisets.
-- NULL = exercice isolé ; exercices consécutifs partageant un même block_id = même superset.
ALTER TABLE exercice ADD COLUMN block_id BIGINT NULL;
