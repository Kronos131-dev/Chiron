#!/usr/bin/env bash
set -uo pipefail

file=$(jq -r '.tool_input.file_path // empty')

[[ "$file" == *.java ]] || exit 0
[[ -f "$file" ]] || exit 0
[[ "$file" != */.claude/* ]] || exit 0

repo_root=$(cd "$(dirname "$file")" && git rev-parse --show-toplevel 2>/dev/null) || exit 0
[[ -f "$repo_root/chiron-back/pom.xml" ]] || exit 0

single_file_pattern=".*$(basename "$file" | sed 's/\./\\./g')"

if ! output=$(cd "$repo_root/chiron-back" && mvn -q spotless:apply -DspotlessFiles="$single_file_pattern" --no-transfer-progress 2>&1); then
  {
    echo "spotless:apply failed on $(basename "$file") — the file most likely does not parse."
    printf '%s\n' "$output" | grep -v '^WARNING' | tail -15
  } >&2
  exit 2
fi

exit 0
