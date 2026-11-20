"""Apply M7-owned PostgreSQL migrations without touching core LMS Flyway history."""

from __future__ import annotations

import hashlib
import os
import re
from importlib.resources import files

import psycopg


MIGRATION_NAME = re.compile(r"V([1-9][0-9]*)__[a-z0-9_]+\.sql$")
LOCK_ID = 0x0AE70001


class MigrationDriftError(RuntimeError):
    """An applied migration no longer matches its recorded checksum."""


def migration_files() -> list[tuple[int, str, str]]:
    items: list[tuple[int, str, str]] = []
    root = files("oep_ai_tutor.migrations")
    for path in root.iterdir():
        match = MIGRATION_NAME.fullmatch(path.name)
        if match is not None:
            items.append((int(match.group(1)), path.name, path.read_text(encoding="utf-8")))
    items.sort(key=lambda item: item[0])
    if [item[0] for item in items] != list(range(1, len(items) + 1)):
        raise ValueError("M7 migration versions must start at 1 and be consecutive")
    return items


def migrate(dsn: str) -> list[int]:
    """Apply all pending migrations atomically under one PostgreSQL advisory lock."""
    if not dsn:
        raise ValueError("AI_TUTOR_DATABASE_URL is required for M7 migrations")
    applied: list[int] = []
    with psycopg.connect(dsn) as connection:
        with connection.cursor() as cursor:
            cursor.execute("SELECT pg_advisory_xact_lock(%s)", (LOCK_ID,))
            cursor.execute("CREATE SCHEMA IF NOT EXISTS ai_tutor")
            cursor.execute(
                """CREATE TABLE IF NOT EXISTS ai_tutor.schema_migrations (
                    version integer PRIMARY KEY,
                    name text NOT NULL,
                    checksum char(64) NOT NULL,
                    applied_at timestamptz NOT NULL DEFAULT now()
                )"""
            )
            for version, name, sql in migration_files():
                checksum = hashlib.sha256(sql.encode("utf-8")).hexdigest()
                cursor.execute(
                    "SELECT name, checksum FROM ai_tutor.schema_migrations WHERE version = %s",
                    (version,),
                )
                recorded = cursor.fetchone()
                if recorded is not None:
                    if recorded != (name, checksum):
                        raise MigrationDriftError(f"M7 migration V{version} changed after application")
                    continue
                cursor.execute(sql)
                cursor.execute(
                    "INSERT INTO ai_tutor.schema_migrations (version, name, checksum) VALUES (%s, %s, %s)",
                    (version, name, checksum),
                )
                applied.append(version)
    return applied


def main() -> None:
    applied = migrate(os.environ.get("AI_TUTOR_DATABASE_URL", ""))
    print("Applied M7 migrations: " + (", ".join(map(str, applied)) if applied else "none"))


if __name__ == "__main__":
    main()
