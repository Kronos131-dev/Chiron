-- Type de groupage : SUPERSET (exos antagonistes) ou BISET (même muscle).
-- NULL = exercice isolé (cohérent avec block_id NULL).
ALTER TABLE exercice ADD COLUMN block_type VARCHAR(16) NULL;
