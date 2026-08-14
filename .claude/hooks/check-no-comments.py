#!/usr/bin/env python3

"""PostToolUse guard: Chiron production code carries no comments.

Only the lines this edit added are inspected, so the comments already in the codebase never
fire — conformance arrives file by file as they are touched.

Reads a Claude Code hook payload on stdin, exits 2 with a message on stderr to warn.
"""

import json
import os
import re
import subprocess
import sys

JAVA_PRAGMAS = (
    "NOSONAR",
    "spotless:off",
    "spotless:on",
    "CHECKSTYLE",
    "@formatter:off",
    "@formatter:on",
)

TYPESCRIPT_PRAGMAS = (
    "eslint-disable",
    "eslint-enable",
    "@ts-expect-error",
    "@ts-ignore",
    "@ts-nocheck",
    "prettier-ignore",
    "NOSONAR",
)

TYPESCRIPT_EXTENSIONS = (".ts", ".js")

JAVA_STRING_LITERAL = re.compile(r'"""(?:.|\n)*?"""' + r'|"(?:\\.|[^"\\])*"' + r"|'(?:\\.|[^'\\])*'")

TYPESCRIPT_STRING_LITERAL = re.compile(
    r'"(?:\\.|[^"\\])*"' + r"|'(?:\\.|[^'\\])*'" + r"|`(?:\\.|[^`\\])*`"
)

JAVA_SCOPE = os.path.join("chiron-back", "src", "main", "java")

TYPESCRIPT_SCOPE = os.path.join("chiron-front", "src")

JAVA_GUIDANCE = (
    "Chiron production code carries no comments. Remove them and make the code say it "
    "instead: extract a named method, or rename the variable so the intent is obvious. "
    "Comments are only allowed as // Given / // When / // Then in src/test/java."
)

TYPESCRIPT_GUIDANCE = (
    "Chiron frontend code carries no comments. Remove them and make the code say it "
    "instead: extract a named function, a computed signal or a child component, or rename "
    "the variable so the intent is obvious."
)


def language_rules_for(path):
    if path.endswith(".java"):
        if JAVA_SCOPE not in path:
            return None
        return JAVA_PRAGMAS, JAVA_STRING_LITERAL, JAVA_GUIDANCE

    if path.endswith(TYPESCRIPT_EXTENSIONS):
        if TYPESCRIPT_SCOPE not in path:
            return None
        if ".spec." in path or ".test." in path:
            return None
        return TYPESCRIPT_PRAGMAS, TYPESCRIPT_STRING_LITERAL, TYPESCRIPT_GUIDANCE

    return None


def lines_added_by_this_edit(repo_root, path):
    diff = subprocess.run(
        ["git", "-C", repo_root, "diff", "--unified=0", "HEAD", "--", path],
        capture_output=True,
        text=True,
    )
    if diff.returncode != 0:
        return []

    added = [
        line[1:]
        for line in diff.stdout.splitlines()
        if line.startswith("+") and not line.startswith("+++")
    ]
    if added:
        return added

    is_tracked = subprocess.run(
        ["git", "-C", repo_root, "ls-files", "--error-unmatch", path],
        capture_output=True,
        text=True,
    )
    if is_tracked.returncode != 0:
        try:
            with open(path, encoding="utf-8") as handle:
                return handle.read().splitlines()
        except OSError:
            return []
    return []


def find_repo_root(path):
    current = os.path.dirname(os.path.abspath(path))
    while current != "/":
        if os.path.isdir(os.path.join(current, ".git")):
            return current
        current = os.path.dirname(current)
    return None


def is_comment(line, pragmas, string_literal):
    if any(pragma in line for pragma in pragmas):
        return False
    code_without_strings = string_literal.sub("", line)
    stripped = code_without_strings.strip()
    return (
        stripped.startswith("//")
        or stripped.startswith("/*")
        or stripped.startswith("*")
        or bool(re.search(r"\S\s+//", code_without_strings))
    )


def main():
    try:
        payload = json.load(sys.stdin)
    except (json.JSONDecodeError, ValueError):
        return 0

    path = (payload.get("tool_input") or {}).get("file_path")
    if not path:
        return 0
    if "/.claude/" in path:
        return 0

    rules = language_rules_for(path)
    if rules is None:
        return 0
    pragmas, string_literal, guidance = rules

    repo_root = find_repo_root(path)
    if not repo_root:
        return 0

    offenders = [
        line.strip()
        for line in lines_added_by_this_edit(repo_root, path)
        if is_comment(line, pragmas, string_literal)
    ]
    if not offenders:
        return 0

    preview = "\n".join(f"    {line}" for line in offenders[:5])
    print(
        f"Comments were added to {os.path.relpath(path, repo_root)}:\n{preview}\n\n{guidance}",
        file=sys.stderr,
    )
    return 2


if __name__ == "__main__":
    sys.exit(main())
