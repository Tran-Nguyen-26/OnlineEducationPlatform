#!/bin/bash
set -e
if [ -z "$(ls -A "$PGDATA" 2>/dev/null)" ]; then
  until pg_isready -h postgres -p 5432 -U oep; do sleep 2; done
  PGPASSWORD=replicator pg_basebackup \
    -h postgres -p 5432 -U replicator \
    -D "$PGDATA" -Fp -Xs -R -C -S replica_1 -P
  chmod 700 "$PGDATA"
fi
exec postgres
