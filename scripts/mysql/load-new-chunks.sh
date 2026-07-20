#!/usr/bin/env bash
# Loads only the seed chunk files not yet recorded in seed_files_applied.
# Each file is applied in its own transaction; a failure mid-file leaves
# nothing committed for that file, so the next run retries it cleanly.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DATA_DIR="${DATA_DIR:-$SCRIPT_DIR/../../src/main/resources/data}"
MYSQL_DEFAULTS_FILE="${MYSQL_DEFAULTS_FILE:-$HOME/.my.cnf}"
MYSQL="mysql --defaults-extra-file=$MYSQL_DEFAULTS_FILE"

applied="$($MYSQL -N -e 'SELECT filename FROM seed_files_applied')"

new_count=0
for f in "$DATA_DIR"/licensed-business-records-*.sql; do
  name="$(basename "$f")"
  if grep -qxF "$name" <<<"$applied"; then
    continue
  fi
  {
    echo "START TRANSACTION;"
    cat "$f"
    printf "INSERT INTO seed_files_applied (filename) VALUES ('%s');\n" "$name"
    echo "COMMIT;"
  } | $MYSQL
  echo "applied $name"
  new_count=$((new_count + 1))
done

echo "done: $new_count new chunk(s) applied"
