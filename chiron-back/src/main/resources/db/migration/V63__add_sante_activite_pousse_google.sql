-- V63 : distinguer la séance qu'on a écrite dans Google Health d'un exercice que la montre a
-- détecté toute seule.
-- Depuis qu'on pousse la séance, Google calcule l'activité autour de l'intervalle qu'on lui a
-- donné : ses calories et sa fréquence moyenne portent alors sur la bonne durée et peuvent aller
-- au journal telles quelles. L'externalId ne permet pas de faire la différence — la
-- synchronisation le remplit aussi depuis un exercice détecté, dont la fenêtre est plus courte
-- que la vraie séance et dont les chiffres seraient donc faux.
ALTER TABLE sante_activite
    ADD COLUMN pousse_google BOOLEAN NOT NULL DEFAULT FALSE;
