"""Native pgvector search must never cross course, lesson, space or revision scope."""

from __future__ import annotations

import os
import math
from uuid import UUID, uuid4

import httpx
import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo
import pytest

from oep_ai_tutor.migrate import migrate
from oep_ai_tutor.pgvector_store import PgvectorStore
from oep_ai_tutor.vector_store import VectorRecord, vector_store_from_env


class FakePinecone:
    """Exercise Pinecone's JSON API and metadata filters without account credentials."""

    def __init__(self):
        self.vectors: dict[str, dict] = {}
        self.queries: list[dict] = []
        self.fail_delete = False

    def __call__(self, request: httpx.Request) -> httpx.Response:
        import json
        body = json.loads(request.content)
        assert request.headers["Api-Key"] == "test-key"
        assert request.headers["X-Pinecone-Api-Version"] == "2025-04"
        if request.url.path == "/vectors/upsert":
            for vector in body["vectors"]:
                self.vectors[vector["id"]] = vector
            return httpx.Response(200, json={"upsertedCount": len(body["vectors"])})
        if request.url.path == "/query":
            self.queries.append(body)
            conditions = body["filter"]["$and"]
            matches = []
            for stored in self.vectors.values():
                metadata = stored["metadata"]
                if not all(
                    metadata.get(key) == expression["$eq"] if "$eq" in expression
                    else metadata.get(key) in expression["$in"]
                    for condition in conditions for key, expression in condition.items()
                ):
                    continue
                values = stored["values"]
                query = body["vector"]
                score = sum(a * b for a, b in zip(values, query)) / (
                    math.sqrt(sum(a * a for a in values)) *
                    math.sqrt(sum(b * b for b in query)))
                matches.append({"id": stored["id"], "score": score})
            matches.sort(key=lambda match: -match["score"])
            return httpx.Response(200, json={"matches": matches[:body["topK"]]})
        if request.url.path == "/vectors/delete":
            if self.fail_delete:
                return httpx.Response(503, json={"error": "unavailable"})
            revision = body["filter"]["revision_id"]["$eq"]
            self.vectors = {key: vector for key, vector in self.vectors.items()
                            if vector["metadata"]["revision_id"] != revision}
            return httpx.Response(200, json={})
        if request.url.path == "/describe_index_stats":
            return httpx.Response(200, json={"dimension": 3})
        return httpx.Response(404)


def _store(dsn: str, kind: str):
    if kind == "pgvector":
        return PgvectorStore(dsn), None
    fake = FakePinecone()
    client = httpx.Client(transport=httpx.MockTransport(fake))
    store = vector_store_from_env(client=client, environ={
        "VECTOR_STORE": "pinecone", "AI_TUTOR_DATABASE_URL": dsn,
        "AI_TUTOR_EMBEDDING_DIMENSION": "3", "PINECONE_INDEX_HOST": "https://index.pinecone.io",
        "PINECONE_API_KEY": "test-key", "PINECONE_NAMESPACE": "test",
    })
    return store, fake


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
        assert migrate(isolated_dsn) == [1, 2, 3]
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
@pytest.mark.parametrize("kind", ["pgvector", "pinecone"])
def test_vector_query_is_course_lesson_space_and_version_scoped(vector_dsn: str, kind: str) -> None:
    course_a, course_b = uuid4(), uuid4()
    lesson_a, locked_lesson, lesson_b = uuid4(), uuid4(), uuid4()
    with psycopg.connect(vector_dsn) as connection:
        content_a, revision_a, chunk_a = _source(connection, course_a, lesson_a, "space-a")
        _, revision_locked, chunk_locked = _source(connection, course_a, locked_lesson, "space-a")
        _, revision_b, chunk_b = _source(connection, course_b, lesson_b, "space-a")
        _, revision_other, chunk_other = _source(connection, course_a, lesson_a, "space-b")
    store, fake = _store(vector_dsn, kind)
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
    if fake is not None:
        assert fake.queries[0]["filter"]["$and"][-1]["revision_id"]["$in"] == [str(revision_a)]
    store.deactivate_revision(revision_b)
    assert store.query(course_id=course_b, lesson_ids=(lesson_b,), space_key="space-a",
                       vector=[1, 0, 0], limit=5) == []
    assert store.health() is True


@pytest.mark.integration
@pytest.mark.parametrize("kind", ["pgvector", "pinecone"])
def test_vector_upsert_rejects_scope_and_dimension_mismatch(vector_dsn: str, kind: str) -> None:
    course, lesson = uuid4(), uuid4()
    with psycopg.connect(vector_dsn) as connection:
        _, revision, chunk = _source(connection, course, lesson, "space-check")
    store, _ = _store(vector_dsn, kind)
    with pytest.raises(ValueError, match="dimension"):
        store.upsert(VectorRecord(chunk, revision, course, lesson, "space-check", [1, 2]))
    with pytest.raises(ValueError, match="scope/dimension"):
        store.upsert(VectorRecord(chunk, revision, uuid4(), lesson, "space-check", [1, 0, 0]))
    with pytest.raises(ValueError, match="nonzero"):
        store.upsert(VectorRecord(chunk, revision, course, lesson, "space-check", [0, 0, 0]))


@pytest.mark.integration
def test_pinecone_revalidates_untrusted_ids_and_deactivation_survives_delete_failure(vector_dsn: str) -> None:
    course, lesson = uuid4(), uuid4()
    with psycopg.connect(vector_dsn) as connection:
        _, revision, chunk = _source(connection, course, lesson, "space-safe")
    store, fake = _store(vector_dsn, "pinecone")
    assert fake is not None
    store.upsert(VectorRecord(chunk, revision, course, lesson, "space-safe", [1, 0, 0]))
    foreign = uuid4()
    fake.vectors[str(foreign)] = {"id": str(foreign), "values": [1, 0, 0],
                                  "metadata": fake.vectors[str(chunk)]["metadata"]}
    assert [hit.chunk_id for hit in store.query(
        course_id=course, lesson_ids=(lesson,), space_key="space-safe",
        vector=[1, 0, 0], limit=5)] == [chunk]
    fake.fail_delete = True
    with pytest.raises(httpx.HTTPStatusError):
        store.deactivate_revision(revision)
    assert store.query(course_id=course, lesson_ids=(lesson,), space_key="space-safe",
                       vector=[1, 0, 0], limit=5) == []
