"""Native pgvector search must never cross course, lesson, space or revision scope."""

from __future__ import annotations

import os
from uuid import UUID, uuid4

import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo
import pytest

from oep_ai_tutor.migrate import migrate
from oep_ai_tutor.pgvector_store import PgvectorStore
from oep_ai_tutor.vector_store import VectorRecord


@pytest.fixture(scope="module")
def vector_dsn():
    base_dsn = os.environ.get("AI_TUTOR_TEST_DATABASE_URL")
    if not base_dsn:
        pytest.skip("Set AI_TUTOR_TEST_DATABASE_URL to an isolated pgvector PostgreSQL database")
    database_name = "oep_ai_vector_" + uuid4().hex
    with psycopg.connect(base_dsn, autocommit=True) as admin:
        admin.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(database_name)))
    try:
        isolated_dsn = make_conninfo(base_dsn, dbname=database_name)
        assert migrate(isolated_dsn) == [1, 2]
        yield isolated_dsn
    finally:
        with psycopg.connect(base_dsn, autocommit=True) as admin:
            admin.execute(sql.SQL("DROP DATABASE {} WITH (FORCE)").format(
                sql.Identifier(database_name)))


def _source(connection, course: UUID, lesson: UUID, space: str,
            *, content: UUID | None = None, version: int = 1, state: str = "READY") -> tuple[UUID, UUID, UUID]:
    content_id, revision_id, chunk_id = content or uuid4(), uuid4(), uuid4()
    connection.execute(
        "INSERT INTO ai_tutor.ai_source_revisions "
        "(id, content_id, version_number, course_id, lesson_id, content_type, "
        "source_title, state, embedding_space_key) "
        "VALUES (%s, %s, %s, %s, %s, 'TEXT', 'Lesson', %s, %s)",
        (revision_id, content_id, version, course, lesson, state, space),
    )
    connection.execute(
        "INSERT INTO ai_tutor.ai_source_chunks "
        "(id, source_revision_id, course_id, lesson_id, chunk_index, chunk_text, "
        "token_count, embedding_space_key, embedding_dimension) "
        "VALUES (%s, %s, %s, %s, 0, 'Grounding', 2, %s, 3)",
        (chunk_id, revision_id, course, lesson, space),
    )
    return content_id, revision_id, chunk_id


@pytest.mark.integration
def test_pgvector_query_is_course_lesson_space_and_version_scoped(vector_dsn: str) -> None:
    course_a, course_b = uuid4(), uuid4()
    lesson_a, locked_lesson, lesson_b = uuid4(), uuid4(), uuid4()
    with psycopg.connect(vector_dsn) as connection:
        content_a, revision_a, chunk_a = _source(connection, course_a, lesson_a, "space-a")
        _, revision_locked, chunk_locked = _source(connection, course_a, locked_lesson, "space-a")
        _, revision_b, chunk_b = _source(connection, course_b, lesson_b, "space-a")
        _, revision_other, chunk_other = _source(connection, course_a, lesson_a, "space-b")
    store = PgvectorStore(vector_dsn)
    for chunk, revision, course, lesson, space in (
        (chunk_a, revision_a, course_a, lesson_a, "space-a"),
        (chunk_locked, revision_locked, course_a, locked_lesson, "space-a"),
        (chunk_b, revision_b, course_b, lesson_b, "space-a"),
        (chunk_other, revision_other, course_a, lesson_a, "space-b"),
    ):
        store.upsert(VectorRecord(chunk, revision, course, lesson, space, [1, 0, 0]))
    hits = store.query(course_id=course_a, lesson_ids=(lesson_a,), space_key="space-a",
                       vector=[1, 0, 0], limit=5)
    assert [hit.chunk_id for hit in hits] == [chunk_a]
    assert hits[0].score == pytest.approx(1.0)
    assert store.query(course_id=course_a, lesson_ids=(), space_key="space-a",
                       vector=[1, 0, 0], limit=5) == []
    assert [hit.chunk_id for hit in store.query(
        course_id=course_b, lesson_ids=(lesson_b,), space_key="space-a",
        vector=[1, 0, 0], limit=5)] == [chunk_b]

    with psycopg.connect(vector_dsn) as connection:
        _source(connection, course_a, lesson_a, "space-a", content=content_a,
                version=2, state="PENDING")
    assert store.query(course_id=course_a, lesson_ids=(lesson_a,), space_key="space-a",
                       vector=[1, 0, 0], limit=5) == []
    store.deactivate_revision(revision_b)
    assert store.query(course_id=course_b, lesson_ids=(lesson_b,), space_key="space-a",
                       vector=[1, 0, 0], limit=5) == []
    assert store.health() is True


@pytest.mark.integration
def test_pgvector_upsert_rejects_scope_and_dimension_mismatch(vector_dsn: str) -> None:
    course, lesson = uuid4(), uuid4()
    with psycopg.connect(vector_dsn) as connection:
        _, revision, chunk = _source(connection, course, lesson, "space-check")
    store = PgvectorStore(vector_dsn)
    with pytest.raises(ValueError, match="scope/dimension"):
        store.upsert(VectorRecord(chunk, revision, course, lesson, "space-check", [1, 2]))
    with pytest.raises(ValueError, match="scope/dimension"):
        store.upsert(VectorRecord(chunk, revision, uuid4(), lesson, "space-check", [1, 0, 0]))
    with pytest.raises(ValueError, match="nonzero"):
        store.upsert(VectorRecord(chunk, revision, course, lesson, "space-check", [0, 0, 0]))
