"""Course-scoped readiness derived from the latest M7 source revision."""

from __future__ import annotations

from datetime import datetime, timezone
from uuid import UUID

from fastapi import APIRouter, Depends, Request

from oep_ai_tutor.db import database
from oep_ai_tutor.errors import AiTutorError
from oep_ai_tutor.security import StudentAssertion, require_student_assertion


router = APIRouter(prefix="/internal/v1/ai/courses/{courseId}", include_in_schema=False)


@router.get("/status")
def course_status(request: Request, courseId: UUID,
                  identity: StudentAssertion = Depends(require_student_assertion)) -> dict:
    space_key = request.app.state.embedding_space_key
    if not space_key:
        raise AiTutorError("AI_TUTOR_UNAVAILABLE", 503, "AI Tutor is unavailable")
    with database(request) as connection:
        rows = connection.execute(
            "SELECT state, count(*) AS source_count FROM ("
            "  SELECT DISTINCT ON (content_id) content_id, state "
            "  FROM ai_tutor.ai_source_revisions "
            "  WHERE course_id = %s AND embedding_space_key = %s "
            "  ORDER BY content_id, version_number DESC, created_at DESC, id DESC"
            ") latest GROUP BY state",
            (identity.course_id, space_key),
        ).fetchall()
    counts = {row["state"]: row["source_count"] for row in rows}
    ready = counts.get("READY", 0)
    indexing = counts.get("PENDING", 0) + counts.get("PROCESSING", 0)
    failed = counts.get("FAILED", 0) + counts.get("UNSUPPORTED", 0)
    if ready == 0:
        state = "INDEXING" if indexing else "NOT_READY"
    else:
        state = "DEGRADED" if indexing or failed else "READY"
    return {
        "course_id": courseId,
        "status": state,
        "ready_sources": ready,
        "indexing_sources": indexing,
        "failed_sources": failed,
        "checked_at": datetime.now(timezone.utc),
    }
