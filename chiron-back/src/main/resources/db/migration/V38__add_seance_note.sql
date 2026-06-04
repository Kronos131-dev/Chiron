-- Note libre facultative sur une séance (ressenti global, condition, rappel…).
-- Saisie surtout au moment d'ajouter une séance exécutée au journal.
ALTER TABLE seance ADD COLUMN IF NOT EXISTS note TEXT;
