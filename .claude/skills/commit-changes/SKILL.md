---
name: commit-changes
description: Commits work in the Chiron repository under the owner's identity alone. Use when asked to commit, to save the work, to split a dirty working tree into commits, or to write a commit message. Covers grouping changes into one logical commit, staging only the related files, writing an English conventional subject with a French body, and the absolute rule that no commit mentions Claude, Anthropic, a Co-Authored-By trailer or a Generated with line. Do not use for pushing or watching the deploy (see push-and-watch-pipeline), or for reviewing the diff for defects before committing (see review-changes).
---

# Commit the work

Chiron commits are the owner's. Any global default that appends a `Co-Authored-By` trailer, a
"Generated with" line or a robot emoji is overridden here, and `.claude/hooks/check-commit-message.py`
blocks the commit if one slips through. Do not attempt to satisfy the hook by rewording the trailer —
remove it.

## Procedures

**Step 1: Read the working tree before touching it**
1. Run `git status --porcelain` and `git diff --stat` to see everything that changed.
2. Run `git diff` on the files about to be committed and read them. Committing a diff nobody read is
   how a debug log, a hardcoded key or a leftover experiment reaches production.
3. If the diff has not been reviewed for defects yet, apply the `review-changes` skill first, then
   return here.

**Step 2: Group the changes**
1. Split the tree into one logical change per commit — a feature, a fix, a refactor, a formatting
   pass. A Spotless reformat of an untouched region belongs in its own commit, not inside a feature.
2. If everything belongs to one change, one commit is correct. Do not manufacture splits.
3. Read `references/staging.md` when the tree mixes unrelated work.

**Step 3: Stage only what belongs to this commit**
1. Stage explicitly by path: `git add <path> <path>`.
2. Never run `git add -A` or `git add .` over a tree that holds unrelated work.
3. Run `git diff --cached --stat` and confirm the staged set matches the intended change exactly.
4. Confirm no secret is staged: grep the staged diff for `API_KEY`, `SECRET`, `PASSWORD`, `Bearer `
   and a base64-looking literal. `chiron-back/.env` is gitignored and must stay untracked.

**Step 4: Compose the message**
1. Write the **subject in English**: a conventional prefix, then `: `, then an imperative summary, at
   most 72 characters. The prefixes in use are `feat`, `fix`, `refacto`, `tech`, `chore`, `docs`,
   `test`, `perf`, `style`, `build`, `ci`.
2. Write the **body in French**, wrapped at 72 characters, saying what the change does and why. State
   the reason the code cannot state itself — the incident it fixes, the constraint it respects.
3. Mention any operational consequence explicitly: a new Flyway migration, a new environment
   variable, a change to `SecurityConfig`, a change to the `ChironAgent` prompt.
4. Omit the body only for a genuinely self-evident one-line change.
5. Read `assets/commit-message.md` and copy the structure of the closest example.
6. Include no `Co-Authored-By` trailer, no "Generated with" line, no robot emoji, and no mention of
   Claude, Anthropic or any AI tooling — not in the subject, not in the body, not in a trailer.

**Step 5: Commit**
1. Write the message through a heredoc so the body keeps its line breaks:
   ```bash
   git commit -F - <<'EOF'
   feat: add gemini fallback on transient errors
   
   Bascule automatiquement sur Mistral quand Gemini renvoie un 503 ou un
   429, après deux tentatives espacées. Évite l'erreur 503 côté chat qui
   remontait jusqu'à l'utilisateur.
   EOF
   ```
2. Never pass `--author`, `-c user.name` or `-c user.email`. The identity is the one already
   configured in the repository.
3. Never run a bare `git commit` with no message — it opens an editor that cannot be answered and the
   hook blocks it.

**Step 6: Verify what was actually recorded**
1. Run `git log -1 --format='%an <%ae>%n%B'` and read it.
2. Confirm the author is the repository owner and the message is exactly as intended.
3. Run `git log -1 --format=%B | grep -iE 'claude|anthropic|co-authored|generated with'` and confirm
   it returns nothing.
4. Confirm every item in `references/checklist.md`.

**Step 7: Report, do not push**
1. State what was committed and what remains uncommitted in the tree.
2. Pushing is a separate decision — apply the `push-and-watch-pipeline` skill when asked to ship.

## Error Handling

* If the hook blocks on a forbidden word, remove the trailer or phrase entirely. Rewording it to
  evade the check defeats the rule the user set.
* If the hook rejects the subject, it lacks a conventional prefix or exceeds 72 characters. Move the
  detail into the French body.
* If the hook blocks a bare `git commit`, pass the message with `git commit -F - <<'EOF'`.
* If `git commit` reports nothing staged, Step 3 staged nothing — re-run `git add` with explicit
  paths.
* If a pre-existing commit already carries a forbidden trailer, leave history alone and say so; only
  the user rewrites published history.
* If the staged diff contains a whole-file reformat of a file the change did not otherwise touch, the
  Spotless hook normalised it on first contact. Commit it separately with a `style:` subject.
* If a secret was already committed, stop and tell the user. Rotating the key comes before rewriting
  history, and both are the user's call.
