#!/usr/bin/env bash
# Compute the next Flyway migration filename for Chiron.
#
#   next-migration.sh add_serie_tempo        -> prints chiron-back/.../V44__add_serie_tempo.sql
#   next-migration.sh add_serie_tempo --create -> also creates the file with a header
#
# The number is one past the highest V<n> present, which is not the same as the file count:
# V34 to V36 were deleted after having been applied in production.

set -euo pipefail

description=${1:-}
mode=${2:-}

if [[ -z "$description" ]]; then
  echo "usage: next-migration.sh <snake_case_description> [--create]" >&2
  exit 1
fi

if [[ ! "$description" =~ ^[a-z0-9]+(_[a-z0-9]+)*$ ]]; then
  echo "Invalid description '$description'." >&2
  echo "Use lowercase words joined by single underscores, e.g. add_serie_tempo." >&2
  exit 1
fi

repo_root=$(git rev-parse --show-toplevel 2>/dev/null) || {
  echo "Not inside the Chiron git repository." >&2
  exit 1
}

migration_dir="$repo_root/chiron-back/src/main/resources/db/migration"

if [[ ! -d "$migration_dir" ]]; then
  echo "Migration directory not found at $migration_dir" >&2
  exit 1
fi

highest=$(
  find "$migration_dir" -maxdepth 1 -name 'V*__*.sql' -printf '%f\n' \
    | sed -n 's/^V\([0-9]\+\)__.*\.sql$/\1/p' \
    | sort -n \
    | tail -1
)

if [[ -z "$highest" ]]; then
  echo "No existing migration matched V<n>__*.sql in $migration_dir" >&2
  exit 1
fi

next=$((highest + 1))
filename="V${next}__${description}.sql"
path="$migration_dir/$filename"

if [[ -e "$path" ]]; then
  echo "$path already exists — another migration claimed this number." >&2
  exit 1
fi

if [[ "$mode" == "--create" ]]; then
  cat > "$path" <<EOF
-- V${next}__${description}.sql
EOF
  echo "Created $path" >&2
fi

echo "$path"
