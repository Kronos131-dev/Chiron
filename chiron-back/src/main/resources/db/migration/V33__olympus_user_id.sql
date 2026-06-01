-- Refonte de la liaison Olympus : on abandonne le token d'intégration chiffré au
-- profit de l'identifiant Olympus de l'utilisateur, résolu une fois à la liaison
-- (vérification pseudo + mot de passe BCrypt contre la base Olympus). Toutes les
-- lectures nutrition/poids passent ensuite directement en base Olympus par cet id.
ALTER TABLE utilisateur ADD COLUMN olympus_user_id BIGINT;

-- Les colonnes de token deviennent obsolètes. Les utilisateurs déjà liés repassent
-- « non liés » et devront se relier une fois (l'ancien mécanisme étant cassé).
ALTER TABLE utilisateur DROP COLUMN olympus_token_encrypted;
ALTER TABLE utilisateur DROP COLUMN olympus_token_expires_at;
-- olympus_username et olympus_linked_at sont conservés (affichage du statut).
