"""Durable receipt of versioned M2 content events before asynchronous indexing."""

from __future__ import annotations

from dataclasses import dataclass
from uuid import UUID, uuid4, uuid5

import psycopg
from psycopg.rows import dict_row

from oep_ai_tutor.errors import AiTutorError


@dataclass(frozen=True)
class IndexEvent:
    event_id: UUID
    content_id: UUID
    course_id: UUID
    lesson_id: UUID
    version_number: int
    action: str

    def __post_init__(self) -> None:
        if not all(isinstance(value, UUID) for value in (
            self.event_id, self.content_id, self.course_id, self.lesson_id
        )) or type(self.version_number) is not int or self.version_number < 1 or self.action not in (
            "UPSERT", "DEACTIVATE"
        ):
            raise ValueError("Invalid versioned index event")


def enqueue_index_event(connection: psycopg.Connection, event: IndexEvent) -> tuple[dict, bool]:
    """Persist one event; exact redelivery returns its original job, reuse conflicts.

    The caller owns the transaction. A broker consumer must acknowledge delivery only
    after this transaction commits; the indexing worker later claims QUEUED jobs.
    """
    with connection.transaction():
        with connection.cursor(row_factory=dict_row) as cursor:
            row = cursor.execute(
                "INSERT INTO ai_tutor.ai_index_jobs "
                "(id, event_id, content_id, course_id, lesson_id, version_number, action, status) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, 'QUEUED') "
                "ON CONFLICT (event_id) DO NOTHING "
                "RETURNING id, event_id, content_id, course_id, lesson_id, version_number, "
                "action, status, attempt_count, error_code, created_at, updated_at",
                (uuid4(), event.event_id, event.content_id, event.course_id,
                 event.lesson_id, event.version_number, event.action),
            ).fetchone()
            if row is not None:
                return dict(row), True
            row = cursor.execute(
                "SELECT id, event_id, content_id, course_id, lesson_id, version_number, "
                "action, status, attempt_count, error_code, created_at, updated_at "
                "FROM ai_tutor.ai_index_jobs WHERE event_id = %s",
                (event.event_id,),
            ).fetchone()
            if row is None:
                raise RuntimeError("Index event disappeared during deduplication")
            if any(row[field] != getattr(event, field) for field in (
                "event_id", "content_id", "course_id", "lesson_id", "version_number", "action"
            )):
                raise AiTutorError("INDEX_EVENT_CONFLICT", 409, "Event ID has a different payload")
            return dict(row), False


def get_index_job(connection: psycopg.Connection, job_id: UUID) -> dict | None:
    with connection.cursor(row_factory=dict_row) as cursor:
        row = cursor.execute(
            "SELECT id, event_id, content_id, course_id, lesson_id, version_number, "
            "action, status, attempt_count, error_code, created_at, updated_at "
            "FROM ai_tutor.ai_index_jobs WHERE id = %s",
            (job_id,),
        ).fetchone()
    return dict(row) if row is not None else None


def superseded_event(connection: psycopg.Connection, event: IndexEvent) -> bool:
    """Fence a delayed UPSERT if a newer version or same-version revocation exists.

    Workers must check this again in their final source-state transaction, because
    a newer event can arrive after a job has been claimed.
    """
    with connection.cursor(row_factory=dict_row) as cursor:
        row = cursor.execute(
            "SELECT EXISTS (SELECT 1 FROM ai_tutor.ai_index_jobs AS newer "
            "WHERE newer.content_id = %s AND (newer.version_number > %s "
            "OR (newer.version_number = %s AND newer.action = 'DEACTIVATE' "
            "AND %s = 'UPSERT'))) AS superseded",
            (event.content_id, event.version_number, event.version_number, event.action),
        ).fetchone()
    return bool(row["superseded"])


DEACTIVATION_NAMESPACE = UUID("dc40a121-57d7-42d7-bb38-7a4cc23c7195")


def deactivate_source(connection: psycopg.Connection, *, content_id: UUID,
                      course_id: UUID, lesson_id: UUID, version_number: int) -> list[UUID]:
    """Commit a durable tombstone and hide every embedding space before acknowledgement.

    The caller may retry remote vector deletion using the returned revision IDs.
    Retrieval checks PostgreSQL state, so a failed remote cleanup cannot expose text.
    """
    event_id = uuid5(DEACTIVATION_NAMESPACE, f"{content_id}:{version_number}")
    event = IndexEvent(event_id, content_id, course_id, lesson_id,
                       version_number, "DEACTIVATE")
    with connection.transaction():
        job, _ = enqueue_index_event(connection, event)
        with connection.cursor(row_factory=dict_row) as cursor:
            revisions = cursor.execute(
                "SELECT id FROM ai_tutor.ai_source_revisions "
                "WHERE content_id = %s AND version_number = %s FOR UPDATE",
                (content_id, version_number),
            ).fetchall()
            ids = [row["id"] for row in revisions]
            cursor.execute(
                "UPDATE ai_tutor.ai_source_revisions SET state = 'INACTIVE', "
                "deactivated_at = coalesce(deactivated_at, now()), updated_at = now() "
                "WHERE content_id = %s AND version_number = %s",
                (content_id, version_number),
            )
            cursor.execute(
                "UPDATE ai_tutor.ai_source_chunks SET embedding = NULL "
                "WHERE source_revision_id = ANY(%s)", (ids,),
            )
            cursor.execute(
                "UPDATE ai_tutor.ai_index_jobs SET status = 'SUCCEEDED', updated_at = now() "
                "WHERE id = %s AND status <> 'SUCCEEDED'", (job["id"],),
            )
        return ids
