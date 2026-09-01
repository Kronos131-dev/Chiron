-- V59 : la liste des scopes Google accordés ne tient plus dans 255 caractères.
-- Depuis l'ajout du scope d'écriture, la chaîne renvoyée par Google fait 296 caractères ; la
-- liaison partait alors en « value too long for type character varying(255) » au commit du
-- callback, la transaction était annulée et l'athlète repartait non lié, sur une page d'erreur
-- brute. Google ajoute des scopes au fil de ses versions : la colonne devient TEXT plutôt que
-- de viser une nouvelle borne qui sera fausse à son tour.
ALTER TABLE utilisateur ALTER COLUMN fitbit_scope TYPE TEXT;
