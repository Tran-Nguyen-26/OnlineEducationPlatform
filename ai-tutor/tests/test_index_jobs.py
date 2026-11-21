"""Versioned indexing events survive redelivery and out-of-order arrival."""

from __future__ import annotations

import os
from datetime import datetime, timezone
from uuid import uuid4

import jwt
import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo
from psycopg.rows import dict_row
import pytest
from fastapi.testclient import TestClient

from oep_ai_tutor.app import create_app
from oep_ai_tutor.errors import AiTutorError
from oep_ai_tutor.index_jobs import IndexEvent, enqueue_index_event, get_index_job, superseded_event
from oep_ai_tutor.migrate import migrate
from oep_ai_tutor.pgvector_store import PgvectorStore
from oep_ai_tutor.security import AUDIENCE, ISSUER
from oep_ai_tutor.vector_store import VectorRecord


SECRET = "index-test-secret-with-at-least-32-bytes"


def _token(request_id, *, scope="ai:index", subject="oep-core-lms", **overrides):
    now = int(datetime.now(timezone.utc).timestamp())
    claims = {"iss": ISSUER, "aud": AUDIENCE, "sub": subject, "scope": scope,
              "request_id": str(request_id), "jti": str(uuid4()),
              "iat": now, "nbf": now, "exp": now + 45}
    claims.update(overrides)
    return jwt.encode(claims, SECRET, algorithm="HS256")


@pytest.fixture(scope="module")
def job_dsn():
    base_dsn = os.environ.get("AI_TUTOR_TEST_DATABASE_URL")
    if not base_dsn:
        pytest.skip("Set AI_TUTOR_TEST_DATABASE_URL to an isolated pgvector PostgreSQL database")
    database_name = "oep_ai_jobs_" + uuid4().hex
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


@pytest.mark.integration
def test_index_event_receipt_is_durable_and_exactly_deduplicated(job_dsn: str) -> None:
    event = IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UPSERT")
    with psycopg.connect(job_dsn, autocommit=True) as connection:
        job, created = enqueue_index_event(connection, event)
        assert created is True
        assert job["status"] == "QUEUED" and job["attempt_count"] == 0
        replay, created = enqueue_index_event(connection, event)
        assert created is False and replay == job
        assert get_index_job(connection, job["id"]) == job
        with pytest.raises(AiTutorError) as conflict:
            enqueue_index_event(connection, IndexEvent(
                event.event_id, event.content_id, event.course_id,
                event.lesson_id, 2, "UPSERT"))
        assert (conflict.value.code, conflict.value.status) == ("INDEX_EVENT_CONFLICT", 409)
        assert get_index_job(connection, job["id"]) == job


@pytest.mark.integration
def test_delayed_upsert_is_fenced_by_newer_version_or_revocation(job_dsn: str) -> None:
    content_id, course_id, lesson_id = uuid4(), uuid4(), uuid4()
    first = IndexEvent(uuid4(), content_id, course_id, lesson_id, 1, "UPSERT")
    second = IndexEvent(uuid4(), content_id, course_id, lesson_id, 2, "UPSERT")
    revoke = IndexEvent(uuid4(), content_id, course_id, lesson_id, 2, "DEACTIVATE")
    with psycopg.connect(job_dsn, autocommit=True) as connection:
        enqueue_index_event(connection, first)
        assert superseded_event(connection, first) is False
        enqueue_index_event(connection, second)
        assert superseded_event(connection, first) is True
        assert superseded_event(connection, second) is False
        enqueue_index_event(connection, revoke)
        assert superseded_event(connection, second) is True
        assert superseded_event(connection, revoke) is False
        delayed = IndexEvent(uuid4(), content_id, course_id, lesson_id, 1, "UPSERT")
        enqueue_index_event(connection, delayed)
        assert superseded_event(connection, delayed) is True
        unrelated = IndexEvent(uuid4(), uuid4(), course_id, lesson_id, 1, "UPSERT")
        enqueue_index_event(connection, unrelated)
        assert superseded_event(connection, unrelated) is False


def test_invalid_index_event_is_rejected() -> None:
    with pytest.raises(ValueError, match="Invalid versioned"):
        IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 0, "UPSERT")
    with pytest.raises(ValueError, match="Invalid versioned"):
        IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UNKNOWN")


@pytest.mark.integration
def test_private_index_routes_enforce_service_scope_and_event_replay(
    job_dsn: str, monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    monkeypatch.setenv("AI_TUTOR_DATABASE_URL", job_dsn)
    client = TestClient(create_app())
    request_id = uuid4()
    headers = {"X-Request-Id": str(request_id),
               "Authorization": "Bearer " + _token(request_id)}
    body = {"event_id": str(uuid4()), "content_id": str(uuid4()),
            "course_id": str(uuid4()), "lesson_id": str(uuid4()),
            "version_number": 1, "action": "UPSERT"}
    path = "/internal/v1/ai/index-jobs"
    for denied in (
        {}, {**headers, "Authorization": "Bearer " + _token(request_id, scope="ai:student")},
        {**headers, "Authorization": "Bearer " + _token(uuid4())},
        {**headers, "Authorization": "Bearer " + _token(request_id, subject="student")},
    ):
        assert client.post(path, json=body, headers=denied).status_code == 401
    created = client.post(path, json=body, headers=headers)
    assert created.status_code == 202
    job = created.json()
    assert job["event_id"] == body["event_id"] and job["status"] == "QUEUED"
    assert client.post(path, json=body, headers=headers).status_code == 200
    assert client.get(path + "/" + job["id"], headers=headers).json() == job
    assert client.get(path + "/" + str(uuid4()), headers=headers).status_code == 404
    conflict = client.post(path, json={**body, "version_number": 2}, headers=headers)
    assert conflict.status_code == 409
    assert conflict.json()["error"]["code"] == "INDEX_EVENT_CONFLICT"
    assert client.post(path, json={**body, "unexpected": "value"}, headers=headers).status_code == 422


@pytest.mark.integration
def test_deactivation_tombstone_hides_vectors_and_fences_late_upsert(
    job_dsn: str, monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    monkeypatch.setenv("AI_TUTOR_DATABASE_URL", job_dsn)
    client = TestClient(create_app())
    request_id = uuid4()
    headers = {"X-Request-Id": str(request_id),
               "Authorization": "Bearer " + _token(request_id)}
    course, lesson, content, revision, chunk = (uuid4() for _ in range(5))
    with psycopg.connect(job_dsn) as connection:
        connection.execute(
            "INSERT INTO ai_tutor.ai_source_revisions "
            "(id, content_id, version_number, course_id, lesson_id, content_type, "
            "source_title, state, embedding_space_key) "
            "VALUES (%s, %s, 1, %s, %s, 'TEXT', 'Lesson', 'READY', 'space')",
            (revision, content, course, lesson),
        )
        connection.execute(
            "INSERT INTO ai_tutor.ai_source_chunks "
            "(id, source_revision_id, course_id, lesson_id, chunk_index, chunk_text, "
            "token_count, embedding_space_key, embedding_dimension) "
            "VALUES (%s, %s, %s, %s, 0, 'Grounding', 2, 'space', 3)",
            (chunk, revision, course, lesson),
        )
    store = PgvectorStore(job_dsn)
    store.upsert(VectorRecord(chunk, revision, course, lesson, "space", [1, 0, 0]))
    assert [hit.chunk_id for hit in store.query(
        course_id=course, lesson_ids=(lesson,), space_key="space",
        vector=[1, 0, 0], limit=5)] == [chunk]
    path = f"/internal/v1/ai/sources/{content}/deactivate"
    body = {"course_id": str(course), "lesson_id": str(lesson), "version_number": 1}
    assert client.post(path, json=body, headers=headers).status_code == 204
    assert client.post(path, json=body, headers=headers).status_code == 204
    assert store.query(course_id=course, lesson_ids=(lesson,), space_key="space",
                       vector=[1, 0, 0], limit=5) == []
    with psycopg.connect(job_dsn, row_factory=dict_row) as connection:
        state = connection.execute(
            "SELECT state FROM ai_tutor.ai_source_revisions WHERE id = %s", (revision,)
        ).fetchone()["state"]
        assert state == "INACTIVE"
        delayed = IndexEvent(uuid4(), content, course, lesson, 1, "UPSERT")
        enqueue_index_event(connection, delayed)
        assert superseded_event(connection, delayed) is True
    assert client.post(path, json={**body, "lesson_id": str(uuid4())},
                       headers=headers).status_code == 409


@pytest.mark.integration
def test_deactivation_before_upsert_is_durable(job_dsn: str, monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    monkeypatch.setenv("AI_TUTOR_DATABASE_URL", job_dsn)
    client = TestClient(create_app())
    request_id = uuid4()
    headers = {"X-Request-Id": str(request_id),
               "Authorization": "Bearer " + _token(request_id)}
    course, lesson, content = uuid4(), uuid4(), uuid4()
    path = f"/internal/v1/ai/sources/{content}/deactivate"
    body = {"course_id": str(course), "lesson_id": str(lesson), "version_number": 3}
    assert client.post(path, json=body, headers=headers).status_code == 204
    with psycopg.connect(job_dsn) as connection:
        late = IndexEvent(uuid4(), content, course, lesson, 3, "UPSERT")
        enqueue_index_event(connection, late)
        assert superseded_event(connection, late) is True
