# Splitting a mixed working tree

## Deciding where the seams are

Group by *reason to change*, not by directory. A backend endpoint and the `chiron-api.ts` method that
calls it are one change; a formatting pass over an unrelated file is another.

Seams that almost always deserve their own commit in Chiron:

| Kind | Prefix | Why separate |
|------|--------|--------------|
| A Spotless reformat of a file the change barely touched | `style:` | It swamps the real diff on first contact with a file |
| A Flyway migration plus the entity field it backs | with the feature | They are meaningless apart, and `ddl-auto: validate` fails if either ships alone |
| A change to the `ChironAgent` `@SystemMessage` | with the tool it describes | The prompt and the tool registration are one capability |
| A prettier pass over `chiron-front` | `style:` | Same reason as Spotless |
| A translation added to `fr.ts` and `en.ts` | with the screen using it | Split apart, one side renders raw keys |
| An unrelated fix noticed in passing | its own `fix:` | It has its own reason to exist and its own revert |

## Staging part of a file

`git add -p` is interactive and cannot be driven from a non-interactive session. When only part of a
file belongs to this commit:

1. Commit the whole file if the extra content is trivially related.
2. Otherwise revert the unrelated part in the editor, commit, then reapply it — the working tree is
   the staging area here.
3. Never use `git stash` to park the difference: the guard blocks the destructive git verbs, and a
   forgotten stash is how work disappears.

## Checking what is staged

```bash
git diff --cached --stat        # the file list
git diff --cached               # the exact content
git status --porcelain          # what is left behind
```

Every file in the first list must have a reason to be in this commit. If one does not, unstage it
with `git reset <path>` — that form touches only the index, not the working tree.

## What must never be staged

- `chiron-back/.env` and any file holding a key. It is gitignored; if it appears, something removed
  the ignore rule.
- `chiron-back/target/`, `chiron-front/dist/`, `chiron-front/node_modules/`, `chiron-front/android/`
  build output.
- `.claude/settings.local.json` — personal, gitignored.
- A migration file edited rather than added. The hook blocks the edit; if one is staged anyway, drop
  it and add a new `V<n>` instead.
