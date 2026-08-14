#!/usr/bin/env bash
set -uo pipefail

file=$(jq -r '.tool_input.file_path // empty')

[[ -n "$file" ]] || exit 0
[[ -f "$file" ]] || exit 0

case "$file" in
  */.claude/*) exit 0 ;;
  */chiron-front/*) ;;
  *) exit 0 ;;
esac

case "$file" in
  *.ts | *.js | *.html | *.css | *.scss | *.json | *.md) ;;
  *) exit 0 ;;
esac

repo_root=$(cd "$(dirname "$file")" && git rev-parse --show-toplevel 2>/dev/null) || exit 0
[[ -f "$repo_root/chiron-front/.prettierrc" ]] || exit 0

if ! output=$(cd "$repo_root/chiron-front" && npx --no-install prettier --write "$file" 2>&1); then
  {
    echo "prettier failed on $(basename "$file") — the file most likely does not parse."
    printf '%s\n' "$output" | tail -10
  } >&2
  exit 2
fi

exit 0
