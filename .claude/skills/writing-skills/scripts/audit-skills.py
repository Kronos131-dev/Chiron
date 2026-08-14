#!/usr/bin/env python3

import os
import re
import sys

SKILL_ROOT = os.path.join(".claude", "skills")
ALLOWED_SUBDIRS = {"scripts", "references", "assets"}
FORBIDDEN_FILES = {"README.md", "CHANGELOG.md", "INSTALLATION.md", "INSTALLATION_GUIDE.md"}
FIRST_PERSON = {"i", "me", "my", "we", "our", "you", "your"}
MAX_DESCRIPTION = 1024
MAX_LINES = 500

FRONTMATTER = re.compile(r"^---\n(.*?)\n---\n", re.S)
POINTER = re.compile(r"`((?:references|assets|scripts)/[^`]+)`")


def audit_skill(skill_dir):
    name = os.path.basename(skill_dir)
    problems = []

    skill_md = os.path.join(skill_dir, "SKILL.md")
    if not os.path.isfile(skill_md):
        return [f"{name}: no SKILL.md"]

    with open(skill_md, encoding="utf-8") as handle:
        text = handle.read()

    match = FRONTMATTER.match(text)
    if not match:
        return [f"{name}: SKILL.md has no YAML frontmatter"]

    frontmatter = match.group(1)
    declared = re.search(r"^name:\s*(.+)$", frontmatter, re.M)
    described = re.search(r"^description:\s*(.+)$", frontmatter, re.M | re.S)

    if not declared:
        problems.append(f"{name}: frontmatter has no name")
    elif declared.group(1).strip() != name:
        problems.append(
            f"{name}: frontmatter name '{declared.group(1).strip()}' does not match directory"
        )

    if not re.fullmatch(r"[a-z0-9]+(-[a-z0-9]+)*", name):
        problems.append(
            f"{name}: directory name must be lowercase alphanumerics joined by single hyphens"
        )

    if not described:
        problems.append(f"{name}: frontmatter has no description")
    else:
        description = described.group(1).strip()
        if len(description) > MAX_DESCRIPTION:
            problems.append(
                f"{name}: description is {len(description)} characters, max {MAX_DESCRIPTION}"
            )
        if "Do not use" not in description and "Don't use" not in description:
            problems.append(f"{name}: description has no negative trigger")
        words = set(re.findall(r"\b\w+\b", description.lower()))
        forbidden = FIRST_PERSON & words
        if forbidden:
            problems.append(
                f"{name}: description uses first/second person {sorted(forbidden)}"
            )

    line_count = text.count("\n") + 1
    if line_count > MAX_LINES:
        problems.append(f"{name}: SKILL.md is {line_count} lines, max {MAX_LINES}")

    if "## Procedures" not in text:
        problems.append(f"{name}: SKILL.md has no '## Procedures' section")
    if "## Error Handling" not in text:
        problems.append(f"{name}: SKILL.md has no '## Error Handling' section")

    for entry in os.listdir(skill_dir):
        path = os.path.join(skill_dir, entry)
        if entry in FORBIDDEN_FILES:
            problems.append(f"{name}: contains forbidden human-facing file {entry}")
        if os.path.isdir(path):
            if entry not in ALLOWED_SUBDIRS:
                problems.append(
                    f"{name}: unexpected directory '{entry}', "
                    f"only {sorted(ALLOWED_SUBDIRS)} are allowed"
                )
                continue
            for child in os.listdir(path):
                if os.path.isdir(os.path.join(path, child)):
                    problems.append(f"{name}: nested directory {entry}/{child} is not flat")

    for pointer in set(POINTER.findall(text)):
        target = pointer.split()[0]
        if not os.path.exists(os.path.join(skill_dir, target)):
            problems.append(f"{name}: SKILL.md points at missing file {target}")

    for subdir in ALLOWED_SUBDIRS:
        path = os.path.join(skill_dir, subdir)
        if not os.path.isdir(path):
            continue
        for child in sorted(os.listdir(path)):
            if os.path.isdir(os.path.join(path, child)):
                continue
            if f"{subdir}/{child}" not in text:
                problems.append(f"{name}: {subdir}/{child} is never referenced from SKILL.md")

    return problems


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else os.getcwd()
    all_problems = []
    audited = 0

    base = os.path.join(root, SKILL_ROOT)
    if os.path.isdir(base):
        for entry in sorted(os.listdir(base)):
            skill_dir = os.path.join(base, entry)
            if not os.path.isdir(skill_dir):
                continue
            audited += 1
            all_problems.extend(audit_skill(skill_dir))

    if audited == 0:
        print(
            f"No skill directory found under {SKILL_ROOT}/ in {root}. "
            "Run this from the Chiron repository root.",
            file=sys.stderr,
        )
        return 1

    if all_problems:
        print("\n".join(all_problems), file=sys.stderr)
        print(
            f"\n{len(all_problems)} problem(s) across {audited} skill(s).",
            file=sys.stderr,
        )
        return 1

    print(f"SUCCESS: {audited} skills conform to the specification.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
