# Commit checklist

## Content
* [ ] The full diff of every staged file was read, not just the file list.
* [ ] The staged set is exactly one logical change.
* [ ] No unrelated file is staged.
* [ ] No secret, key, token or password is in the diff.
* [ ] `chiron-back/.env` is not staged.
* [ ] No build output (`target/`, `dist/`, `node_modules/`, `android/`) is staged.
* [ ] No already-applied Flyway migration was modified.

## Message
* [ ] Subject is in English.
* [ ] Subject carries a conventional prefix and is at most 72 characters.
* [ ] Subject is imperative and describes the change, not the process.
* [ ] Body is in French and explains what and why.
* [ ] A new migration, environment variable, `SecurityConfig` rule or prompt change is named in the
      body.
* [ ] No `Co-Authored-By` trailer.
* [ ] No "Generated with" line.
* [ ] No robot emoji.
* [ ] No mention of Claude, Anthropic or any AI tooling anywhere in the message.

## Execution
* [ ] The message was passed with `git commit -F - <<'EOF'`, not a chain of `-m` flags.
* [ ] No `--author`, `-c user.name` or `-c user.email` was passed.
* [ ] `git log -1 --format='%an <%ae>%n%B'` shows the repository owner and the intended message.
* [ ] `git log -1 --format=%B | grep -iE 'claude|anthropic|co-authored|generated with'` returns
      nothing.
* [ ] What remains uncommitted in the tree was reported to the user.
