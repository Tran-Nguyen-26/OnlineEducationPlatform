from __future__ import annotations

import os
from uuid import uuid4

import psycopg
import pytest

from oep_ai_tutor.migrate import MigrationDriftError, migrate, migration_files


def test_migration_versions_are_consecutive_and_database_url_is_required() -> None:
    assert [version for version, _, _ in migration_files()] == [1]
    with pytest.raises(ValueError, match="AI_TUTOR_DATABASE_URL"):
        migrate("")


@pytest.mark.integration
def test_postgres_schema_migrates_idempotently_and_enforces_ownership_constraints(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    dsn = os.environ.get("AI_TUTOR_TEST_DATABASE_URL")
    if not dsn:
        pytest.skip("Set AI_TUTOR_TEST_DATABASE_URL to an isolated PostgreSQL database")

    assert migrate(dsn) == [1]
    assert migrate(dsn) == []

    with psycopg.connect(dsn) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                "SELECT count(*) FROM ai_tutor.schema_migrations WHERE version = 1"
            )
            assert cursor.fetchone() == (1,)
            cursor.execute(
                "SELECT count(*) FROM information_schema.tables "
                "WHERE table_schema = 'ai_tutor' AND table_name LIKE 'ai_%'"
            )
            assert cursor.fetchone() == (7,)
            cursor.execute(
                "SELECT count(*) FROM information_schema.tables "
                "WHERE table_schema = 'public' AND table_name LIKE 'ai_%'"
            )
            assert cursor.fetchone() == (0,)

            user_id, course_id, conversation_id = uuid4(), uuid4(), uuid4()
            cursor.execute(
                "INSERT INTO ai_tutor.ai_conversations (id, user_id, course_id, title) "
                "VALUES (%s, %s, %s, %s)",
                (conversation_id, user_id, course_id, "Test chat"),
            )
            key = uuid4()
            cursor.execute(
                "INSERT INTO ai_tutor.ai_chat_requests "
                "(id, user_id, conversation_id, idempotency_key, payload_hash, status) "
                "VALUES (%s, %s, %s, %s, %s, 'PROCESSING')",
                (uuid4(), user_id, conversation_id, key, "a" * 64),
            )
            with pytest.raises(psycopg.errors.UniqueViolation):
                with connection.transaction():
                    cursor.execute(
                        "INSERT INTO ai_tutor.ai_chat_requests "
                        "(id, user_id, conversation_id, idempotency_key, payload_hash, status) "
                        "VALUES (%s, %s, %s, %s, %s, 'PROCESSING')",
                        (uuid4(), user_id, conversation_id, key, "a" * 64),
                    )

            content_id = uuid4()
            for version, space, should_succeed in (
                (1, "jina-v5", True),
                (2, "jina-v5", False),
                (1, "openai-v1", True),
            ):
                statement = (
                    "INSERT INTO ai_tutor.ai_source_revisions "
                    "(id, content_id, version_number, course_id, lesson_id, content_type, "
                    "source_title, state, embedding_space_key) "
                    "VALUES (%s, %s, %s, %s, %s, 'TEXT', 'Source', 'READY', %s)"
                )
                values = (uuid4(), content_id, version, course_id, uuid4(), space)
                if should_succeed:
                    cursor.execute(statement, values)
                else:
                    with pytest.raises(psycopg.errors.UniqueViolation):
                        with connection.transaction():
                            cursor.execute(statement, values)

            cursor.execute(
                "SELECT id, lesson_id FROM ai_tutor.ai_source_revisions "
                "WHERE content_id = %s AND embedding_space_key = 'jina-v5' AND state = 'READY'",
                (content_id,),
            )
            revision_id, lesson_id = cursor.fetchone()
            chunk_sql = (
                "INSERT INTO ai_tutor.ai_source_chunks "
                "(id, source_revision_id, course_id, lesson_id, chunk_index, chunk_text, "
                "token_count, embedding_space_key, embedding_dimension) "
                "VALUES (%s, %s, %s, %s, 0, 'Grounded text', 2, 'jina-v5', 1024)"
            )
            with pytest.raises(psycopg.errors.ForeignKeyViolation):
                with connection.transaction():
                    cursor.execute(chunk_sql, (uuid4(), revision_id, uuid4(), lesson_id))
            cursor.execute(chunk_sql, (uuid4(), revision_id, course_id, lesson_id))

            event_id = uuid4()
            job_sql = (
                "INSERT INTO ai_tutor.ai_index_jobs "
                "(id, event_id, content_id, course_id, lesson_id, version_number, action, status) "
                "VALUES (%s, %s, %s, %s, %s, 1, 'UPSERT', 'QUEUED')"
            )
            cursor.execute(job_sql, (uuid4(), event_id, content_id, course_id, lesson_id))
            with pytest.raises(psycopg.errors.UniqueViolation):
                with connection.transaction():
                    cursor.execute(job_sql, (uuid4(), event_id, content_id, course_id, lesson_id))

    original = migration_files()
    monkeypatch.setattr(
        "oep_ai_tutor.migrate.migration_files",
        lambda: [(1, original[0][1], original[0][2] + "\n-- changed")],
    )
    with pytest.raises(MigrationDriftError):
        migrate(dsn)
