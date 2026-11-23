#!/usr/bin/env bash
# Restores a backup into a NEW database (default oep_restore) so it can be verified before any cut-over (restore drill).
set -euo pipefail
FILE="$1"; DB="${2:-oep_restore}"; PG="${PG_CONTAINER:-oep-postgres-1}"
docker exec "$PG" psql -U oep -d postgres -c "DROP DATABASE IF EXISTS $DB" -c "CREATE DATABASE $DB"
gunzip -c "$FILE" | docker exec -i "$PG" psql -U oep -d "$DB" -q -v ON_ERROR_STOP=1 >/dev/null
echo "Restored into $DB. Row counts:"
docker exec "$PG" psql -U oep -d "$DB" -At -c "select 'users='||count(*) from users union all select 'courses='||count(*) from courses union all select 'enrollments='||count(*) from enrollments"
