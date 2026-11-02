-- V64 : une séance faite se souvient du modèle de programme dont elle est issue.
-- Jusqu'ici une séance du journal ne gardait que le titre copié du modèle, modifiable à la main :
-- impossible de dire de quelle séance du programme elle venait. Le pointeur est nul pour les
-- séances antérieures, pour les séances vocales et pour les modèles eux-mêmes ; il retombe à
-- NULL quand le modèle est supprimé, sans jamais bloquer cette suppression.
ALTER TABLE seance ADD COLUMN modele_id BIGINT;
ALTER TABLE seance ADD CONSTRAINT fk_seance_modele
    FOREIGN KEY (modele_id) REFERENCES seance (id) ON DELETE SET NULL;
CREATE INDEX idx_seance_modele ON seance (modele_id) WHERE modele_id IS NOT NULL;
