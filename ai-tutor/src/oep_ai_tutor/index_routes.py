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
