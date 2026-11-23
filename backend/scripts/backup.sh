#!/usr/bin/env bash
# Logical backup of the OEP database + object-store content. Usage: scripts/backup.sh [outdir]
# Restore:  scripts/restore.sh <backup.sql.gz> [target_db]
set -euo pipefail
OUT="${1:-./backups}"; STAMP="$(date +%Y%m%d-%H%M%S)"; mkdir -p "$OUT"
PG="${PG_CONTAINER:-oep-postgres-1}"
docker exec "$PG" pg_dump -U oep -d oep --no-owner --format=plain | gzip > "$OUT/oep-$STAMP.sql.gz"
# keep the 14 most recent backups
ls -1t "$OUT"/oep-*.sql.gz | tail -n +15 | xargs -r rm -f
echo "Backup written: $OUT/oep-$STAMP.sql.gz ($(du -h "$OUT/oep-$STAMP.sql.gz" | cut -f1))"
