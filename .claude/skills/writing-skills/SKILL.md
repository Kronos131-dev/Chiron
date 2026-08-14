---
name: writing-skills
description: Authors and audits the Chiron skills under .claude/skills/ and the enforcement hooks under .claude/hooks/. Use when creating a skill directory, drafting a SKILL.md, choosing a name and description, deciding what belongs in the skill versus CLAUDE.md versus a references file, adding a script or an asset, registering a skill in the CLAUDE.md routing table, or diagnosing why a skill fails to trigger or triggers instead of a neighbouring one. Also use when writing a PreToolUse guard or a PostToolUse side-effect hook and wiring it into .claude/settings.json. Provides validate-metadata.py and audit-skills.py, which mechanically check the specification. Do not use for ordinary project documentation, for the convention files under .claude/conventions/, or for reviewing application code (see review-changes).
---

# Author a skill

A skill is a plain markdown procedure Claude Code loads on demand. It earns its place only if it
carries something a competent reader could not derive from the code in a minute — a silent failure
mode, a registration step with no compiler behind it, a command whose name lies about what it runs.

## How skills are discovered

Claude Code auto-discovers `<project>/.claude/skills/<name>/SKILL.md` when the session is opened on
the repository root. Skills are **not** discovered from inside `chiron-back/` or `chiron-front/`; the
directory is flat, with no per-module nesting. Global skills that apply to every project the user
opens live in `~/.claude/skills/` instead. Scope is conveyed through the `name` and the
`description`, never through the path.

## Procedures

**Step 1: Confirm the skill is warranted and pick its home**
1. Confirm the procedure is Chiron-specific or Chiron-flavoured. A generic explanation of git or
   Angular belongs nowhere.
2. Place it in `.claude/skills/<name>/` when it names Chiron things — `ChironAgent`, `SeanceMapper`,
   `chiron-api.ts`, a Flyway version, the deploy workflow.
3. Place it in `~/.claude/skills/<name>/` only when it carries no project vocabulary at all.
4. Search the existing skills for an overlap before creating a new one. Extending a skill beats
   splitting a task across two that compete for the same request.

**Step 2: Validate the metadata before writing anything**
1. Draft the `name`: 1 to 64 characters, lowercase letters, digits and single hyphens, matching the
   directory name exactly. Use a verb-first name for a procedure (`add-api-endpoint`), a noun for a
   body of knowledge.
2. Draft the `description`: at most 1024 characters, third person, opening with what the skill does
   and when to use it, closing with `Do not use for X (see other-skill)`.
3. Load the description with the vocabulary a real request would contain — file names, class names,
   command names. It is the only text read when deciding whether to load the skill.
4. Execute `python3 .claude/skills/writing-skills/scripts/validate-metadata.py --name "<name>" --description "<description>"`.
5. Correct the metadata from the stderr output and re-run until it succeeds.

**Step 3: Create the directory**
1. Create `.claude/skills/<name>/` using the validated name.
2. Add `scripts/`, `references/` and `assets/` only as needed. Keep them exactly one level deep.
3. Never create a `README.md`, `CHANGELOG.md` or `INSTALLATION.md` inside a skill.

**Step 4: Draft SKILL.md**
1. Copy the structure from `assets/SKILL.template.md`.
2. Open with the load-bearing gotcha, not with a definition. State the cost of getting it wrong.
3. Write a `## Procedures` section as numbered `**Step N:**` blocks in strict chronological order.
4. Write every instruction in the third-person imperative: "Extract the value", "Run the build",
   never "I will" or "you should".
5. Express branches as explicit decision trees: "If the entity gained a field, apply the
   `add-flyway-migration` skill. Otherwise continue at Step 5."
6. Point at supporting files just in time, naming the exact file: "Read `references/checklist.md`".
7. Use the vocabulary the codebase uses — *seance*, *exercice*, *serie*, *tool*, *migration*,
   *authGuard*, *signal*. A synonym the code never uses makes the skill harder to trigger and to
   trust.
8. Close with an `## Error Handling` section mapping concrete failure states to recovery steps.
9. Keep the file under 500 lines.

**Step 5: Extract bulky content**
1. Move large rule sets, inventories and schemas into one topic-named file per subject under
   `references/`.
2. Move anything to be copied verbatim — code skeletons, SQL patterns, message templates — into a
   topic-named file under `assets/`. Command the agent to copy the structure rather than describing
   it in prose.
3. Add `references/checklist.md` holding the final audit, and reference it from the last step of the
   procedure.
4. Verify every supporting file is pointed at from `SKILL.md`. A file nothing points to is never
   read.

**Step 6: Add a script only where it pays**
1. Add one when an operation is fragile or repetitive and any variation would be a bug — computing
   the next Flyway migration number, diffing the two i18n dictionaries.
2. Design it as a tiny CLI taking arguments, writing results to stdout and failures to stderr.
3. Write descriptive failure messages so the agent can self-correct without asking the user.
4. Run `chmod +x` and execute it, including its failure paths, before shipping.
5. Never place library code in `scripts/`.

**Step 7: Verify the content is true**
1. Confirm every file path, class name and command named in the skill exists. Grep for them.
2. Read the code the skill describes rather than trusting existing documentation. `README.md`
   documents Angular 17, Java 17 and a Mistral-only setup, none of which is still true — copying it
   would teach the wrong stack.
3. Take every example from real code in the repository, with the real names.

**Step 8: Validate the whole tree**
1. Execute `python3 .claude/skills/writing-skills/scripts/audit-skills.py` from the repository root.
2. Correct anything it reports and re-run until it passes.

**Step 9: Register and test the trigger**
1. Add the skill to the routing table in `CLAUDE.md`, phrasing the task the way a developer would
   say it.
2. Phrase a request that way, without naming the skill, and confirm it loads.
3. Phrase a neighbouring request and confirm it does **not** load. If two skills compete, sharpen the
   negative trigger in both descriptions.
4. Walk the procedure step by step against a real case in the repository. Anything ambiguous at
   Step 4 will be ambiguous in use.
5. Confirm every item in `references/checklist.md`.

## Author a hook

Hooks run before or after a tool executes, to refuse an action or to apply a side effect. The six
existing hooks live in `.claude/hooks/` and share one protocol, which is what keeps them testable
from a shell.

### Hook protocol

1. The script reads a JSON payload on stdin:
   `{"tool_name": "Edit", "tool_input": {"file_path": "...", "command": "..."}}`.
2. Exit `0` allows or no-ops. Exit `2` blocks a `PreToolUse` and warns on a `PostToolUse`.
3. The message goes to stderr — it is what the agent and the user read when the block fires.
4. The script is standalone: nothing beyond `python3`, `bash` and `jq`.
5. It self-gates. Every hook returns 0 immediately for a path or command it does not own, and skips
   anything under `/.claude/`, so the tooling never reformats or blocks itself.

### Design rules learned from the existing hooks

1. Prefer a **read allowlist with a default deny** over a denylist of dangerous verbs. An unknown
   remote command or MCP tool is refused and the message names the constant to extend —
   `REMOTE_READ_COMMANDS` in `.claude/hooks/guard-commands.py`.
2. Split chained commands on `&&`, `||`, `;`, `|` and newlines before parsing, or a guard is bypassed
   by concatenation.
3. Inspect only what the current edit added, using `git diff --unified=0 HEAD -- <path>`. A hook that
   judges the whole file fires on legacy code and gets ignored.
4. Strip string literals before looking for syntax. `ChironAgent` holds a very large French prompt in
   a literal; a naive scan finds "comments" inside it.
5. End every refusal by handing the decision back to the user. A guard that only says "no" invites a
   workaround.

### Wiring into Claude Code

Add the script to `.claude/settings.json`:

```json
{
  "hooks": {
    "PostToolUse": [
      {
        "matcher": "Edit|Write",
        "hooks": [
          { "type": "command", "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/<script>", "timeout": 60 }
        ]
      }
    ],
    "PreToolUse": [
      {
        "matcher": "Bash|mcp__.*",
        "hooks": [
          { "type": "command", "command": "\"$CLAUDE_PROJECT_DIR\"/.claude/hooks/<script>", "timeout": 15 }
        ]
      }
    ]
  }
}
```

Then test it from a shell, both paths, before trusting it:

```bash
echo '{"tool_name":"Bash","tool_input":{"command":"<the blocked form>"}}' | .claude/hooks/<script>; echo "exit=$?"
echo '{"tool_name":"Bash","tool_input":{"command":"<the allowed form>"}}' | .claude/hooks/<script>; echo "exit=$?"
```

Add the new cases to `scripts/test-hooks.py` and run
`python3 .claude/skills/writing-skills/scripts/test-hooks.py` from the repository root. It drives
every hook through its blocked and allowed paths and must end on `ALL PASS`. A guard is only worth
having if a chained or quoted form cannot walk around it, and that regression is easy to reintroduce
while editing the parser.

## Error Handling

* If `validate-metadata.py` reports `NAME ERROR`, the name contains uppercase letters, consecutive
  hyphens, or a leading or trailing hyphen. Rewrite it as lowercase words joined by single hyphens.
* If it reports `STYLE WARNING`, the description contains first or second person pronouns. Rewrite in
  the third person: "Adds", "Verifies", "Routes".
* If it reports `DESCRIPTION ERROR`, trim the description below 1024 characters by moving detail into
  the body.
* If `audit-skills.py` reports a name mismatch, rename the directory to match the frontmatter, not
  the reverse — the directory name is what other skills cross-reference.
* If it reports a nested directory, flatten it. Supporting files sit exactly one level deep.
* If it reports a dangling pointer, either create the referenced file or remove the pointer.
* If it reports an unreferenced supporting file, point at it from `SKILL.md` or delete it.
* If it reports "No skill directory found", it was run from somewhere other than the repository root.
* If `SKILL.md` exceeds 500 lines, move the largest procedural block into `references/`.
* If a skill never triggers, its description lacks the words a real request would use. Add the
  concrete file, class and command names.
* If the wrong skill triggers, both descriptions overlap. Add an explicit negative trigger naming the
  other skill in both.
* If a hook never fires, confirm the matcher in `.claude/settings.json` covers the tool name and that
  the script has its executable bit set.
* If a hook fires on its own edits, add the `/.claude/` self-exclusion at the top of the script.
