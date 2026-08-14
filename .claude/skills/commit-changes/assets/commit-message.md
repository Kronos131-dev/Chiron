# Commit message templates

Copy the structure. Subject in English with a conventional prefix, blank line, body in French wrapped
at 72 characters. No trailers.

## A feature

```
feat: add gemini fallback on transient errors

Bascule automatiquement sur Mistral quand Gemini renvoie un 503, un 429
ou dépasse le délai, après deux tentatives espacées de 400 ms. La
mémoire de conversation est réinitialisée avant chaque tentative pour
éviter qu'une requête d'outil orpheline ne casse l'appel suivant.
```

## A fix, naming the symptom

```
fix: keep the coach memory keyed by conversation

Le chat repartait de zéro dès qu'on ouvrait une seconde conversation :
la mémoire était indexée par utilisateur, donc la deuxième écrasait la
première. Elle est maintenant indexée par id de conversation.
```

## A schema change — always name the migration

```
feat: track tempo per set

Ajoute le champ tempoSecondes sur Serie, exposé dans SerieDto et saisi
dans l'écran de séance.

Migration V44__add_serie_tempo.sql. Colonne nullable : les séries
existantes restent valides sans reprise de données.
```

## A change that needs an environment variable

```
feat: import boditrax reports from the mailbox

Récupère les rapports Boditrax dans la boîte Gmail au même titre que
les rapports Visbody, et les convertit en BodyCompositionRecord.

Nécessite BODITRAX_MAILBOX_ENABLED dans chiron-back/.env et dans les
secrets GitHub, sinon l'import reste silencieusement inactif.
```

## A refactor

```
refacto: extract the exercise filters out of the component

Sort la logique de filtrage de bibliotheque.ts vers shared/exercise-
filters.ts, déjà utilisée par exercise-picker. Aucun changement de
comportement.
```

## A formatting-only commit

```
style: apply the eclipse formatter to WorkoutTools

Reformatage seul, produit par le hook au premier passage sur le
fichier. Aucun changement de comportement.
```

## A one-line change that needs no body

```
fix: correct the tonnage rounding in the weekly summary
```

## Never

```
feat: add gemini fallback

🤖 Generated with Claude Code

Co-Authored-By: Claude <noreply@anthropic.com>
```

Neither the emoji line, nor the generated line, nor the trailer. The commit is the owner's.
