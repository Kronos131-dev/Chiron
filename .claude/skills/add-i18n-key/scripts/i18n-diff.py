#!/usr/bin/env python3

"""Report keys that exist in only one of the two Chiron i18n dictionaries.

fr.ts is the source of truth; en.ts must mirror it key for key. A key present in one only renders
as the raw key for every user on the other language, and nothing else in the project checks it.

Usage:  python3 .claude/skills/add-i18n-key/scripts/i18n-diff.py [repo_root]
Exits 0 when the dictionaries agree, 1 when they diverge or cannot be parsed.
"""

import os
import re
import sys

DICTIONARY_DIR = os.path.join("chiron-front", "src", "app", "i18n")

ENTRY = re.compile(r"^\s*'([^']+)'\s*:", re.M)


def keys_in(path):
    try:
        with open(path, encoding="utf-8") as handle:
            text = handle.read()
    except OSError as error:
        print(f"Cannot read {path}: {error}", file=sys.stderr)
        return None

    keys = ENTRY.findall(text)
    if not keys:
        print(
            f"No entries matched in {path}. The dictionaries must stay flat string literals "
            "so they remain statically checkable.",
            file=sys.stderr,
        )
        return None

    return keys


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else os.getcwd()
    french_path = os.path.join(root, DICTIONARY_DIR, "fr.ts")
    english_path = os.path.join(root, DICTIONARY_DIR, "en.ts")

    for path in (french_path, english_path):
        if not os.path.isfile(path):
            print(
                f"{path} not found. Run this from the Chiron repository root.",
                file=sys.stderr,
            )
            return 1

    french = keys_in(french_path)
    english = keys_in(english_path)
    if french is None or english is None:
        return 1

    problems = []

    missing_in_english = [k for k in french if k not in set(english)]
    missing_in_french = [k for k in english if k not in set(french)]

    for key in missing_in_english:
        problems.append(f"missing in en.ts: {key}")
    for key in missing_in_french:
        problems.append(f"missing in fr.ts: {key}")

    for name, keys in (("fr.ts", french), ("en.ts", english)):
        seen = set()
        for key in keys:
            if key in seen:
                problems.append(f"duplicate in {name}: {key}")
            seen.add(key)

    if problems:
        print("\n".join(problems), file=sys.stderr)
        print(
            f"\n{len(problems)} problem(s). fr.ts has {len(french)} keys, "
            f"en.ts has {len(english)}.",
            file=sys.stderr,
        )
        return 1

    print(f"SUCCESS: fr.ts and en.ts both declare the same {len(french)} keys.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
