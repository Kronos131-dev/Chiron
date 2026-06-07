/**
 * Présentation préfaite (texte brut) de l'application, affichée via le bouton « i » du
 * header. Volontairement statique : ne déclenche aucun appel IA ni outil. Le rendu se
 * fait en `white-space: pre-line`, donc pas de markdown ici.
 */
export const APP_PRESENTATION_FR = `Bienvenue dans Chiron ⚔️

Chiron est ton sanctuaire d'entraînement : un coach IA doublé d'une suite d'outils pour progresser. Voici tout ce que tu peux faire.

🗣️ Le chat — parle à la voix ou au clavier pour enregistrer tes séances, poser des questions et obtenir des conseils.
📖 Les Annales — l'historique de toutes tes séances réalisées (séries, reps, charges, durée).
📋 Les Programmes — crée et réutilise des modèles de séances, avec supersets et bisets.
🏋️ La Séance — ton interface d'entraînement en direct.
💎 Le Trésor — tes records (1RM, PR) et ton poids de corps, classés par paliers de force.
🏛️ L'Agora — l'espace social : découvre les autres athlètes.
📊 Statistiques — tes graphes de progression : force, volume, muscles, poids, nutrition, récup.
❤️ Données Fitbit — ton tableau de bord santé (sommeil, FC repos, pas…).
🍽️ Nutrition (Olympus) — lie ton compte Olympus dans les Réglages pour suivre calories et macros.
👤 Profil & Réglages — gère tes infos et tes liaisons.

Prêt ? Dis par exemple à Chiron : « J'ai fait 4x8 au développé couché à 80 kg ».`;

export const APP_PRESENTATION_EN = `Welcome to Chiron ⚔️

Chiron is your training sanctuary: an AI coach paired with a suite of tools to help you progress. Here's everything you can do.

🗣️ The chat — speak or type to log your sessions, ask questions and get advice.
📖 The Annals — the history of every session you've completed (sets, reps, loads, duration).
📋 The Programs — create and reuse session templates, with supersets and bisets.
🏋️ The Session — your live training interface.
💎 The Treasure — your records (1RM, PR) and bodyweight, ranked by strength tiers.
🏛️ The Agora — the social space: discover other athletes.
📊 Statistics — your progress charts: strength, volume, muscles, weight, nutrition, recovery.
❤️ Fitbit Data — your health dashboard (sleep, resting HR, steps…).
🍽️ Nutrition (Olympus) — link your Olympus account in Settings to track calories and macros.
👤 Profile & Settings — manage your info and your links.

Ready? Tell Chiron, for example: "I did 4x8 on the bench press at 80 kg".`;

/** @deprecated conservé pour compatibilité ; préférer APP_PRESENTATION_FR / _EN. */
export const APP_PRESENTATION = APP_PRESENTATION_FR;
