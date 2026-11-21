"""Versioned indexing events survive redelivery and out-of-order arrival."""

from __future__ import annotations

import os
from uuid import uuid4

import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo
import pytest

from oep_ai_tutor.errors import AiTutorError
from oep_ai_tutor.index_jobs import IndexEvent, enqueue_index_event, get_index_job, superseded_event
from oep_ai_tutor.migrate import migrate


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
