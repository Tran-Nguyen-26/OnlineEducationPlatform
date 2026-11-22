"""Private M2 indexing job intake and status; never public gateway routes."""

from __future__ import annotations

from typing import Literal
from uuid import UUID

from fastapi import APIRouter, Depends, Request, Response
from pydantic import BaseModel, ConfigDict, Field

from oep_ai_tutor.db import database
from oep_ai_tutor.errors import AiTutorError
from oep_ai_tutor.index_jobs import IndexEvent, deactivate_source, enqueue_index_event, get_index_job
from oep_ai_tutor.security import require_index_assertion
from oep_ai_tutor.vector_store import vector_store_from_env


router = APIRouter(prefix="/internal/v1/ai", include_in_schema=False,
                   dependencies=[Depends(require_index_assertion)])


class IndexJobBody(BaseModel):
    model_config = ConfigDict(extra="forbid")

    event_id: UUID
    content_id: UUID
    course_id: UUID
    lesson_id: UUID
    version_number: int = Field(ge=1)
    action: Literal["UPSERT", "DEACTIVATE"]


class DeactivateBody(BaseModel):
    model_config = ConfigDict(extra="forbid")

    course_id: UUID
    lesson_id: UUID
    version_number: int = Field(ge=1)


class CourseAccessBody(BaseModel):
    model_config = ConfigDict(extra="forbid")

    course_version: int = Field(ge=0)
    active: bool


def _public_job(row: dict) -> dict:
    return {
        "id": row["id"], "event_id": row["event_id"],
        "content_id": row["content_id"], "version_number": row["version_number"],
        "action": row["action"], "status": row["status"],
        "attempts": row["attempt_count"], "error_code": row["error_code"],
        "created_at": row["created_at"], "updated_at": row["updated_at"],
    }


@router.post("/index-jobs")
def enqueue_job(body: IndexJobBody, request: Request, response: Response) -> dict:
    event = IndexEvent(body.event_id, body.content_id, body.course_id,
                       body.lesson_id, body.version_number, body.action)
    with database(request) as connection:
        row, created = enqueue_index_event(connection, event)
    response.status_code = 202 if created else 200
    return _public_job(row)


@router.get("/index-jobs/{jobId}")
def index_job(jobId: UUID, request: Request) -> dict:
    with database(request) as connection:
        row = get_index_job(connection, jobId)
    if row is None:
        raise AiTutorError("AI_INDEX_JOB_NOT_FOUND", 404, "Index job not found")
    return _public_job(row)


@router.get("/courses/{courseId}/index-status")
def course_index_status(courseId: UUID, request: Request) -> dict:
    """Operational source state for M2's Admin route; no text or file grants."""
    space_key = request.app.state.embedding_space_key
    if not space_key:
        raise AiTutorError("AI_TUTOR_UNAVAILABLE", 503, "AI Tutor is unavailable")
    with database(request) as connection:
        rows = connection.execute(
            "WITH latest_revision AS ("
            " SELECT DISTINCT ON (content_id) content_id, version_number, "
            " source_title, state, updated_at FROM ai_tutor.ai_source_revisions "
            " WHERE course_id = %s AND embedding_space_key = %s "
            " ORDER BY content_id, version_number DESC, created_at DESC, id DESC"
            "), latest_job AS ("
            " SELECT DISTINCT ON (content_id) content_id, version_number, action, "
            " status, error_code, updated_at FROM ai_tutor.ai_index_jobs "
            " WHERE course_id = %s ORDER BY content_id, version_number DESC, "
            " action ASC, created_at DESC, id DESC"
            ") SELECT coalesce(revision.content_id, job.content_id) AS content_id, "
            " greatest(revision.version_number, job.version_number) AS version_number, "
            " CASE WHEN job.version_number > coalesce(revision.version_number, 0) "
            " THEN NULL ELSE revision.source_title END AS source_title, "
            " greatest(job.updated_at, revision.updated_at) AS updated_at, "
            " CASE WHEN job.action = 'DEACTIVATE' AND job.version_number >= "
            " coalesce(revision.version_number, 0) THEN 'INACTIVE' "
            " WHEN job.version_number > coalesce(revision.version_number, 0) THEN "
            " CASE WHEN job.status = 'SUCCEEDED' THEN 'INACTIVE' ELSE job.status END "
            " ELSE revision.state END AS state, "
            " CASE WHEN job.version_number > coalesce(revision.version_number, 0) "
            " OR (job.version_number = revision.version_number "
            " AND revision.state IN ('FAILED', 'UNSUPPORTED')) "
            " THEN job.error_code ELSE NULL END AS error_code "
            " FROM latest_revision AS revision FULL OUTER JOIN latest_job AS job "
            " USING (content_id) ORDER BY content_id",
            (courseId, space_key, courseId),
        ).fetchall()
    return {"course_id": courseId, "embedding_space_key": space_key,
            "items": rows}


@router.post("/sources/{contentId}/deactivate", status_code=204)
def deactivate(contentId: UUID, body: DeactivateBody, request: Request) -> Response:
    with database(request) as connection:
        revision_ids = deactivate_source(
            connection, content_id=contentId, course_id=body.course_id,
            lesson_id=body.lesson_id, version_number=body.version_number)
    if revision_ids:
        try:
            store = vector_store_from_env()
            for revision_id in revision_ids:
                store.deactivate_revision(revision_id)
        except Exception as exc:
            raise AiTutorError("AI_TUTOR_UNAVAILABLE", 503, "Source cleanup is unavailable") from exc
    return Response(status_code=204)


@router.put("/courses/{courseId}/access", status_code=204)
def set_course_access(courseId: UUID, body: CourseAccessBody, request: Request) -> Response:
    """Apply M2's versioned publication signal; old requests cannot reopen a course."""
    with database(request) as connection:
        applied = connection.execute(
            "INSERT INTO ai_tutor.ai_course_access (course_id, course_version, active) "
            "VALUES (%s, %s, %s) ON CONFLICT (course_id) DO UPDATE SET "
            "course_version = EXCLUDED.course_version, active = EXCLUDED.active, "
            "updated_at = now() WHERE ai_tutor.ai_course_access.course_version "
            "< EXCLUDED.course_version RETURNING course_version",
            (courseId, body.course_version, body.active),
        ).fetchone()
        if applied is None:
            row = connection.execute(
                "SELECT course_version, active FROM ai_tutor.ai_course_access "
                "WHERE course_id = %s", (courseId,),
            ).fetchone()
            if row["course_version"] == body.course_version and row["active"] != body.active:
                raise AiTutorError("INDEX_EVENT_CONFLICT", 409,
                                   "Course version has a conflicting access state")
    return Response(status_code=204)
