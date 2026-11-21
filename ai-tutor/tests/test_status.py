from __future__ import annotations

import os
from datetime import datetime, timezone
from uuid import UUID, uuid4

import jwt
import psycopg
import pytest
from fastapi.testclient import TestClient

from oep_ai_tutor.app import create_app
from oep_ai_tutor.migrate import migrate
from oep_ai_tutor.index_jobs import IndexEvent, enqueue_index_event


SECRET = "test-only-service-secret-with-at-least-32-bytes"
SPACE = "jina:jina-embeddings-v5-text-small:1024"


def headers(user_id: UUID, course_id: UUID, request_id: UUID) -> dict[str, str]:
    now = int(datetime.now(timezone.utc).timestamp())
    assertion = jwt.encode({
        "iss": "oep-core-lms", "aud": "m7-ai-tutor", "scope": "ai:student",
        "sub": str(user_id), "course_id": str(course_id),
        "request_id": str(request_id), "jti": str(uuid4()),
        "iat": now, "nbf": now, "exp": now + 45,
    }, SECRET, algorithm="HS256")
    return {"Authorization": f"Bearer {assertion}", "X-Request-Id": str(request_id)}


def add_revision(connection: psycopg.Connection, course_id: UUID, content_id: UUID,
                 version: int, state: str, space: str = SPACE) -> UUID:
    revision_id = uuid4()
    connection.execute(
        "INSERT INTO ai_tutor.ai_source_revisions "
        "(id, content_id, version_number, course_id, lesson_id, content_type, "
        "source_title, state, embedding_space_key) "
        "VALUES (%s, %s, %s, %s, %s, 'TEXT', 'Lesson', %s, %s)",
        (revision_id, content_id, version, course_id, uuid4(), state, space),
    )
    return revision_id


@pytest.mark.integration
def test_status_tracks_queued_and_superseding_jobs(monkeypatch: pytest.MonkeyPatch) -> None:
    dsn = os.environ.get("AI_TUTOR_TEST_DATABASE_URL")
    if not dsn:
        pytest.skip("Set AI_TUTOR_TEST_DATABASE_URL to an isolated PostgreSQL database")
    migrate(dsn)
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    monkeypatch.setenv("AI_TUTOR_DATABASE_URL", dsn)
    monkeypatch.setenv("AI_TUTOR_EMBEDDING_SPACE_KEY", SPACE)
    course, lesson, content, user = uuid4(), uuid4(), uuid4(), uuid4()
    path = f"/internal/v1/ai/courses/{course}/status"
    with TestClient(create_app()) as client:
        auth = headers(user, course, uuid4())
        assert client.get(path, headers=auth).json()["status"] == "NOT_READY"
        with psycopg.connect(dsn, autocommit=True) as connection:
            enqueue_index_event(connection, IndexEvent(uuid4(), content, course, lesson, 1, "UPSERT"))
        queued = client.get(path, headers=auth).json()
        assert (queued["status"], queued["indexing_sources"]) == ("INDEXING", 1)
        with psycopg.connect(dsn) as connection:
            connection.execute(
                "INSERT INTO ai_tutor.ai_source_revisions "
                "(id, content_id, version_number, course_id, lesson_id, content_type, "
                "source_title, state, embedding_space_key) "
                "VALUES (%s, %s, 1, %s, %s, 'TEXT', 'Lesson', 'READY', %s)",
                (uuid4(), content, course, lesson, SPACE),
            )
        assert client.get(path, headers=auth).json()["status"] == "READY"
        with psycopg.connect(dsn, autocommit=True) as connection:
            enqueue_index_event(connection, IndexEvent(uuid4(), content, course, lesson, 2, "UPSERT"))
        superseded = client.get(path, headers=auth).json()
        assert (superseded["status"], superseded["ready_sources"],
                superseded["indexing_sources"]) == ("INDEXING", 0, 1)


@pytest.mark.integration
def test_status_counts_latest_sources_in_verified_course_and_active_space(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    dsn = os.environ.get("AI_TUTOR_TEST_DATABASE_URL")
    if not dsn:
        pytest.skip("Set AI_TUTOR_TEST_DATABASE_URL to an isolated PostgreSQL database")
    migrate(dsn)
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    monkeypatch.setenv("AI_TUTOR_DATABASE_URL", dsn)
    monkeypatch.setenv("AI_TUTOR_EMBEDDING_SPACE_KEY", SPACE)
    course_id, other_course, user_id, request_id = uuid4(), uuid4(), uuid4(), uuid4()
    path = f"/internal/v1/ai/courses/{course_id}/status"
    auth = headers(user_id, course_id, request_id)

    with TestClient(create_app()) as client:
        assert client.get(path).status_code == 401
        assert client.get(path, headers=headers(user_id, other_course, request_id)).status_code == 401
        empty = client.get(path, headers=auth)
        assert empty.status_code == 200
        assert empty.json()["course_id"] == str(course_id)
        assert (empty.json()["status"], empty.json()["ready_sources"],
                empty.json()["indexing_sources"], empty.json()["failed_sources"]) == (
                    "NOT_READY", 0, 0, 0)

        content_id = uuid4()
        with psycopg.connect(dsn) as connection:
            first = add_revision(connection, course_id, content_id, 1, "PENDING")
            add_revision(connection, other_course, uuid4(), 1, "READY")
            add_revision(connection, course_id, uuid4(), 1, "READY", "another-space")
        indexing = client.get(path, headers=auth).json()
        assert (indexing["status"], indexing["ready_sources"], indexing["indexing_sources"]) == (
            "INDEXING", 0, 1)

        with psycopg.connect(dsn) as connection:
            connection.execute(
                "UPDATE ai_tutor.ai_source_revisions SET state = 'READY' WHERE id = %s", (first,)
            )
        assert client.get(path, headers=auth).json()["status"] == "READY"

        with psycopg.connect(dsn) as connection:
            add_revision(connection, course_id, uuid4(), 1, "FAILED")
            add_revision(connection, course_id, uuid4(), 1, "UNSUPPORTED")
        degraded = client.get(path, headers=auth).json()
        assert (degraded["status"], degraded["ready_sources"], degraded["failed_sources"]) == (
            "DEGRADED", 1, 2)

        with psycopg.connect(dsn) as connection:
            add_revision(connection, course_id, content_id, 2, "PROCESSING")
        latest = client.get(path, headers=auth).json()
        assert (latest["status"], latest["ready_sources"], latest["indexing_sources"],
                latest["failed_sources"]) == ("INDEXING", 0, 1, 2)


def test_status_requires_an_active_embedding_space(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    monkeypatch.delenv("AI_TUTOR_EMBEDDING_SPACE_KEY", raising=False)
    user_id, course_id, request_id = uuid4(), uuid4(), uuid4()
    with TestClient(create_app()) as client:
        response = client.get(
            f"/internal/v1/ai/courses/{course_id}/status",
            headers=headers(user_id, course_id, request_id),
        )
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "AI_TUTOR_UNAVAILABLE"
