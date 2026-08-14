#!/usr/bin/env python3

"""PostToolUse guard: an applied Flyway migration is never edited.

Flyway stores a checksum per migration. Changing a file that has already run makes the next
startup fail with a validation error on every environment that applied it — production
included. The fix is always a new V<n> file.

Reads a Claude Code hook payload on stdin, exits 2 with a message on stderr to warn.
"""

import json
import os
import re
import subprocess
import sys

MIGRATION_DIRECTORY = os.path.join("db", "migration")

MIGRATION_FILE = re.compile(r"^V\d+__[a-z0-9_]+\.sql$")


def find_repo_root(path):
    current = os.path.dirname(os.path.abspath(path))
    while current != "/":
        if os.path.isdir(os.path.join(current, ".git")):
            return current
        current = os.path.dirname(current)
    return None


def is_tracked(repo_root, path):
    result = subprocess.run(
        ["git", "-C", repo_root, "ls-files", "--error-unmatch", path],
        capture_output=True,
        text=True,
    )
    return result.returncode == 0


def main():
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0

    path = (payload.get("tool_input") or {}).get("file_path")
    if not path:
        return 0

    if MIGRATION_DIRECTORY not in path:
        return 0

    name = os.path.basename(path)
    if not MIGRATION_FILE.match(name):
        print(
            f"{name} does not match the Chiron migration naming pattern V<n>__snake_case.sql.\n"
            "Rename it — Flyway ignores anything else, so the migration would silently never run.",
            file=sys.stderr,
        )
        return 2

    repo_root = find_repo_root(path)
    if not repo_root or not is_tracked(repo_root, path):
        return 0

    print(
        f"{name} is already committed, so it has been applied — editing it breaks its Flyway "
        "checksum and the next startup fails schema validation on every environment that ran it, "
        "production included.\n\n"
        "Revert this edit and express the change as a new migration instead: apply the "
        "add-flyway-migration skill, which computes the next number with "
        ".claude/skills/add-flyway-migration/scripts/next-migration.sh.",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
