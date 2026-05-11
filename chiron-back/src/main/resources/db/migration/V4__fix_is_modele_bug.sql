-- Ce script s'assure que les anciens modèles et historiques sont bien dissociés.
-- Par défaut, quand la colonne is_modele a été ajoutée, TOUTES les séances (modèles et historiques) ont pris la valeur false.
-- C'est pourquoi la page Programme affiche l'historique et la page Journal est vide ou affiche peu de choses.

-- On va réparer ça en utilisant une heuristique :
-- 1. Si une séance a un week_number > 0, c'est probablement un historique (car on l'a calculé lors d'une vraie séance).
-- 2. Si une séance a un titre comme "Nouvelle Routine" et peu d'exercices, c'est potentiellement un modèle, mais on va surtout se baser sur week_number.

-- Toutes les séances qui ont un numéro de semaine valide (> 0) DOIVENT être considérées comme de l'historique (is_modele = true).
UPDATE seance SET is_modele = true WHERE week_number > 0;

-- Toutes les séances qui ont le week_number par défaut (0) ou null DOIVENT être considérées comme des modèles (is_modele = false).
UPDATE seance SET is_modele = false WHERE week_number = 0 OR week_number IS NULL;

-- Note sur la sémantique de 'is_modele':
-- is_modele = false -> C'est un modèle (preset) affiché dans Programme.
-- is_modele = true  -> C'est une séance effectuée affichée dans Journal.
