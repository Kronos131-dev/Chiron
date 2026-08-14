# Skill audit checklist

Every item must pass before the skill is committed.

## 1. Metadata and discovery
* [ ] `name` is 1 to 64 characters, lowercase, digits and single hyphens only.
* [ ] `name` matches the parent directory exactly.
* [ ] `description` is under 1024 characters.
* [ ] `description` states what the skill does, when to use it, and closes with a negative trigger
      naming the competing skill.
* [ ] `description` avoids "I", "me", "my", "we", "our", "you", "your".
* [ ] `description` contains the concrete file, class and command names a real request would use.
* [ ] `scripts/validate-metadata.py` passes on the name and description.

## 2. Location and structure
* [ ] Chiron-specific skills live in `.claude/skills/`; skills with no project vocabulary live in
      `~/.claude/skills/`.
* [ ] Only `scripts/`, `references/` and `assets/` subdirectories are present.
* [ ] Supporting files are exactly one level deep, with no nested subfolders.
* [ ] No `README.md`, `CHANGELOG.md` or `INSTALLATION.md` inside the skill.
* [ ] All paths in SKILL.md are relative and use forward slashes.

## 3. Instructions
* [ ] SKILL.md is under 500 lines.
* [ ] The opening states the load-bearing gotcha, not a definition.
* [ ] A `## Procedures` section exists, written as numbered `**Step N:**` blocks in chronological
      order.
* [ ] Instructions use the third-person imperative: "Extract", "Run", "Confirm".
* [ ] Branches are expressed as explicit decision trees naming the step or skill to jump to.
* [ ] Supporting files are loaded just in time through an explicit pointer.
* [ ] Terminology matches the codebase: seance, exercice, serie, tool, migration, authGuard, signal.
* [ ] An `## Error Handling` section maps concrete failure states to recovery steps.

## 4. Supporting files
* [ ] Bulky rule sets and inventories live in `references/`.
* [ ] Content meant to be copied verbatim lives in `assets/`.
* [ ] A `references/checklist.md` exists and is referenced from the final procedure step.
* [ ] Every file in `scripts/`, `references/` and `assets/` is pointed at from SKILL.md.

## 5. Scripts
* [ ] Each script is a single-purpose CLI taking arguments.
* [ ] Success goes to stdout, failures to stderr with descriptive, actionable messages.
* [ ] Executable bit set, and every path including failures was run once.
* [ ] No library code in `scripts/`.

## 6. Accuracy
* [ ] Every file path, class name and command named in the skill was verified to exist.
* [ ] Examples are copied from real code, with the real names.
* [ ] Claims were checked against the code, not against `README.md`, which documents a stack the
      project has since outgrown.

## 7. Registration and triggering
* [ ] Listed in the skills routing table in `CLAUDE.md`, phrased as a developer would say the task.
* [ ] `scripts/audit-skills.py` passes over the whole repository.
* [ ] A natural request loads the skill without naming it.
* [ ] A neighbouring request does not load it.
* [ ] The procedure was walked step by step against a real case.

## 8. Hooks
* [ ] The script lives in `.claude/hooks/` and follows the JSON-on-stdin protocol.
* [ ] It is standalone — nothing beyond `python3`, `bash` and `jq`.
* [ ] Exit code 0 allows, exit code 2 blocks or warns, messages go to stderr.
* [ ] It self-gates on the paths and commands it does not own, and skips `/.claude/`.
* [ ] Guards use a read allowlist with a default deny, not a denylist.
* [ ] Chained commands are split before parsing.
* [ ] Wired into `.claude/settings.json` under the right matcher, with a timeout.
* [ ] Both the blocked form and the allowed form were driven from a shell and behaved correctly.
