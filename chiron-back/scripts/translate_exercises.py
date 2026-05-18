#!/usr/bin/env python3
"""
Script one-shot : traduit les 873 exercices (nom_fr manquants + description_fr)
via l'API Mistral, puis génère V24__fr_translations_exercises.sql.

Usage :
    MISTRAL_API_KEY=<key> python3 translate_exercises.py
    # ou depuis le dossier chiron-back/ avec le .env :
    export $(cat .env | xargs) && python3 scripts/translate_exercises.py
"""

import json
import os
import re
import sys
import time
import urllib.request
import urllib.error

# ── Configuration ──────────────────────────────────────────────────────────────

MISTRAL_API_KEY = os.environ.get("MISTRAL_API_KEY", "")
MISTRAL_MODEL   = "mistral-large-latest"
BATCH_SIZE      = 20  # exercices par appel API
RETRY_DELAY     = 5   # secondes entre deux tentatives en cas d'erreur

V9_SQL  = os.path.join(os.path.dirname(__file__),
                       "../src/main/resources/db/migration/V9__seed_exercice_definition.sql")
OUT_SQL = os.path.join(os.path.dirname(__file__),
                       "../src/main/resources/db/migration/V24__fr_translations_exercises.sql")

SYSTEM_PROMPT = """Tu es un expert en musculation et fitness, chargé de traduire des fiches d'exercices \
de l'anglais vers le français pour une application de coaching sportif.

RÈGLES DE TRADUCTION :
- Utilise le vocabulaire professionnel des salles de sport françaises
- Les descriptions sont rédigées à l'impératif, à la 2e personne du singulier (tutoiement)
  Ex : "Allonge-toi sur le banc", "Saisis la barre", "Expire lors de l'effort"
- Respecte la structure originale (étapes dans le même ordre, même niveau de détail)
- Garde les anglicismes consacrés dans le milieu fitness français (voir liste ci-dessous)

GLOSSAIRE OBLIGATOIRE (termes déjà établis dans l'app — respecte-les impérativement) :
Barbell → Barre | Dumbbell → Haltère(s) | Cable → Poulie | Band → Élastique
Deadlift → Soulevé de terre | Bench press → Développé couché
Shoulder press → Développé militaire | Incline press → Développé incliné
Overhead press → Développé au-dessus de la tête
Row → Rowing | Pull-up → Traction | Lat pulldown → Tirage vertical
Push-up → Pompes | Dip → Dips | Lunge → Fente
Fly / Flyes → Écarté | Raise → Élévation | Shrug → Haussement d'épaules
Stretch → Étirement | Foam roll / SMR → Auto-massage | Warm-up → Échauffement
Standing → Debout | Seated → Assis | Lying / Lie down → Allongé(e)
Prone → Ventre au sol | Supine → Sur le dos
Alternating → Alterné(e) | Unilateral → Unilatéral | Bilateral → Bilatéral
Grip → Prise | Overhand grip → Prise pronation | Underhand grip → Prise supination
Neutral grip → Prise neutre | Wide grip → Prise large | Narrow grip → Prise serrée
Rep → Répétition | Set → Série | Rest → Repos | Tempo → Tempo
Glutes → Fessiers | Hamstrings → Ischio-jambiers | Quads → Quadriceps
Chest → Pectoraux | Back → Dos | Shoulders → Épaules | Core → Abdominaux / Gainage
Lats → Grand dorsal | Traps → Trapèzes | Delts → Deltoïdes
Biceps / Triceps / Calves / Forearms → inchangés

TERMES À CONSERVER EN ANGLAIS (usage courant dans les salles françaises) :
Squat, Curl, Crunch, Plank, Hip thrust, Good morning, Kettlebell,
Arnold press, Hack squat, Romanian deadlift, Landmine, Rack, Box jump,
Burpee, Clean, Snatch, Jerk, Thruster, Wall ball, HIIT

FORMAT DE SORTIE : JSON array strict, sans texte autour, sans markdown
[{"external_id": "...", "nom_fr": "...", "description_fr": "..."}, ...]"""

USER_PROMPT_TEMPLATE = """Traduis ces {n} exercices de fitness. Pour chacun :
- nom_fr : nom court et professionnel (ex : "Soulevé de terre barre", "Curl marteau alterné", "Écarté poulie basse")
- description_fr : traduction fidèle étape par étape, impératif, tutoiement

Exercices à traduire :
{exercises_json}"""


# ── Parsing V9 SQL ─────────────────────────────────────────────────────────────

def _read_sql_string(content: str, pos: int) -> tuple[str, int]:
    """Lit une chaîne SQL entourée de guillemets simples depuis la position pos.
    Gère les apostrophes échappées ''. Retourne (valeur, nouvelle_position)."""
    assert content[pos] == "'", f"Attendu ' à pos {pos}, trouvé '{content[pos]}'"
    pos += 1
    buf = []
    while pos < len(content):
        ch = content[pos]
        if ch == "'":
            if pos + 1 < len(content) and content[pos + 1] == "'":
                buf.append("'")
                pos += 2
            else:
                pos += 1
                break
        else:
            buf.append(ch)
            pos += 1
    return "".join(buf), pos


def parse_v9_sql(path: str) -> list[dict]:
    """Extrait tous les exercices du fichier V9 SQL via un parser de chaînes SQL."""
    with open(path, encoding="utf-8") as f:
        content = f.read()

    # Trouve le début du bloc VALUES
    values_start = content.index("VALUES")
    pos = values_start + len("VALUES")

    exercises = []

    while pos < len(content):
        # Avance jusqu'au prochain '(' de tuple
        paren = content.find("(", pos)
        if paren == -1:
            break
        pos = paren + 1

        # Ignore les espaces/retours
        while pos < len(content) and content[pos] in (" ", "\n", "\r", "\t"):
            pos += 1

        if pos >= len(content):
            break

        # Champ 1 : nom_fr (NULL ou 'string')
        if content[pos:pos+4] == "NULL":
            nom_fr = None
            pos += 4
        elif content[pos] == "'":
            nom_fr, pos = _read_sql_string(content, pos)
        else:
            # Pas un tuple d'exercice (ex: parenthèse dans un commentaire)
            continue

        # Consomme la virgule
        pos = content.index(",", pos) + 1
        while pos < len(content) and content[pos] in (" ", "\n", "\r", "\t"):
            pos += 1

        # Champ 2 : nom_en
        if content[pos] != "'":
            continue
        nom_en, pos = _read_sql_string(content, pos)

        # Consomme la virgule
        pos = content.index(",", pos) + 1
        while pos < len(content) and content[pos] in (" ", "\n", "\r", "\t"):
            pos += 1

        # Champ 3 : description_en
        if content[pos] != "'":
            continue
        description_en, pos = _read_sql_string(content, pos)

        # Champ 4 : gif_path (on saute)
        pos = content.index(",", pos) + 1
        while pos < len(content) and content[pos] in (" ", "\n", "\r", "\t"):
            pos += 1
        if content[pos] != "'":
            continue
        _, pos = _read_sql_string(content, pos)

        # Champs 5, 6, 7 : enums non quotés (MUSCLE, EQUIPEMENT, DIFFICULTE) — on saute
        for _ in range(3):
            pos = content.index(",", pos) + 1
            while pos < len(content) and content[pos] in (" ", "\n", "\r", "\t"):
                pos += 1
            # Avance jusqu'à la prochaine virgule ou ')'
            end = pos
            while end < len(content) and content[end] not in (",", ")", "'"):
                end += 1
            pos = end

        # Champ 8 : external_id
        pos = content.index(",", pos) + 1
        while pos < len(content) and content[pos] in (" ", "\n", "\r", "\t"):
            pos += 1
        if content[pos] != "'":
            continue
        external_id, pos = _read_sql_string(content, pos)

        exercises.append({
            "external_id":    external_id,
            "nom_en":         nom_en,
            "nom_fr":         nom_fr,
            "description_en": description_en,
        })

    print(f"Exercices parsés : {len(exercises)}")
    print(f"  → avec nom_fr  : {sum(1 for e in exercises if e['nom_fr'])}")
    print(f"  → sans nom_fr  : {sum(1 for e in exercises if not e['nom_fr'])}")
    return exercises


# ── Appel Mistral ──────────────────────────────────────────────────────────────

def call_mistral(batch: list[dict], api_key: str) -> list[dict]:
    """Envoie un batch à Mistral et retourne les traductions."""
    exercises_for_prompt = [
        {
            "external_id":   ex["external_id"],
            "nom_en":        ex["nom_en"],
            **({"nom_fr_existant": ex["nom_fr"]} if ex["nom_fr"] else {}),
            "description_en": ex["description_en"],
        }
        for ex in batch
    ]

    user_content = USER_PROMPT_TEMPLATE.format(
        n=len(batch),
        exercises_json=json.dumps(exercises_for_prompt, ensure_ascii=False, indent=2)
    )

    payload = json.dumps({
        "model": MISTRAL_MODEL,
        "messages": [
            {"role": "system",  "content": SYSTEM_PROMPT},
            {"role": "user",    "content": user_content},
        ],
        "temperature": 0.1,   # low for consistent terminology
        "response_format": {"type": "json_object"},
    }).encode("utf-8")

    req = urllib.request.Request(
        "https://api.mistral.ai/v1/chat/completions",
        data=payload,
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type":  "application/json",
        },
        method="POST",
    )

    with urllib.request.urlopen(req, timeout=120) as resp:
        data = json.loads(resp.read())

    raw = data["choices"][0]["message"]["content"]

    # Mistral avec response_format json_object peut wrapper dans {"translations": [...]}
    parsed = json.loads(raw)
    if isinstance(parsed, list):
        return parsed
    # Cherche le premier champ qui est une liste
    for v in parsed.values():
        if isinstance(v, list):
            return v
    raise ValueError(f"Format inattendu de la réponse Mistral : {raw[:200]}")


# ── Génération SQL ─────────────────────────────────────────────────────────────

def escape_sql(s: str) -> str:
    return s.replace("'", "''")


def generate_sql(translations: list[dict], original_exercises: list[dict]) -> str:
    """Génère le SQL en préservant les nom_fr existants.
    Mistral ne fournit un nouveau nom_fr que pour les 36 exercices sans traduction.
    Pour tous les autres, on conserve le nom_fr du V9.
    """
    original_by_id = {ex["external_id"]: ex for ex in original_exercises}

    lines = [
        "-- V24 : traductions françaises des exercices (généré automatiquement par translate_exercises.py)",
        "-- Script one-shot — ne pas modifier manuellement",
        "",
    ]
    for t in translations:
        ext_id   = t["external_id"]
        original = original_by_id.get(ext_id, {})

        # Conserve le nom_fr existant si présent — Mistral n'est utilisé que pour les 36 sans nom_fr
        nom_fr  = original.get("nom_fr") or t.get("nom_fr") or ""
        desc_fr = t.get("description_fr") or ""

        sql_ext_id  = escape_sql(ext_id)
        sql_nom_fr  = escape_sql(nom_fr)
        sql_desc_fr = escape_sql(desc_fr)

        lines.append(
            f"UPDATE exercice_definition\n"
            f"  SET nom_fr = '{sql_nom_fr}',\n"
            f"      description_fr = '{sql_desc_fr}'\n"
            f"  WHERE external_id = '{sql_ext_id}';\n"
        )
    return "\n".join(lines)


# ── Main ───────────────────────────────────────────────────────────────────────

def main():
    if not MISTRAL_API_KEY:
        print("Erreur : variable MISTRAL_API_KEY manquante.", file=sys.stderr)
        print("Usage : export $(cat chiron-back/.env | xargs) && python3 scripts/translate_exercises.py")
        sys.exit(1)

    exercises = parse_v9_sql(V9_SQL)
    all_translations: list[dict] = []
    batches = [exercises[i:i+BATCH_SIZE] for i in range(0, len(exercises), BATCH_SIZE)]
    total   = len(batches)

    print(f"\nDébut traduction — {len(exercises)} exercices en {total} batches de {BATCH_SIZE}\n")

    for i, batch in enumerate(batches, 1):
        print(f"Batch {i}/{total} ({len(batch)} exercices)...", end=" ", flush=True)
        for attempt in range(1, 4):
            try:
                results = call_mistral(batch, MISTRAL_API_KEY)
                all_translations.extend(results)
                print(f"OK ({len(results)} traduits)")
                break
            except Exception as e:
                if attempt == 3:
                    print(f"ECHEC après 3 tentatives : {e}", file=sys.stderr)
                    sys.exit(1)
                print(f"tentative {attempt} échouée ({e}), retry dans {RETRY_DELAY}s...", end=" ")
                time.sleep(RETRY_DELAY)
        time.sleep(0.5)  # évite le rate-limit Mistral

    print(f"\nTraductions reçues : {len(all_translations)}")

    sql = generate_sql(all_translations, exercises)
    out_path = os.path.abspath(OUT_SQL)
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(sql)

    print(f"Migration générée : {out_path}")
    print("\nProchaine étape : relire un échantillon du SQL, puis lancer le backend.")
    print("Flyway appliquera V24 automatiquement au démarrage.")


if __name__ == "__main__":
    main()
