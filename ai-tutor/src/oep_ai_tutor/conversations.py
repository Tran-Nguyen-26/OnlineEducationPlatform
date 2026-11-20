"""Private, owner-scoped conversation and message read endpoints."""

from __future__ import annotations

from uuid import UUID, uuid4

import psycopg
from fastapi import APIRouter, Depends, Query, Request
from pydantic import BaseModel, ConfigDict, Field, field_validator

from oep_ai_tutor.db import database
from oep_ai_tutor.errors import AiTutorError
from oep_ai_tutor.security import StudentAssertion, require_student_assertion


router = APIRouter(prefix="/internal/v1/ai/courses/{courseId}", include_in_schema=False)


class CreateConversation(BaseModel):
    model_config = ConfigDict(extra="forbid")
    title: str = Field(default="New chat", min_length=1, max_length=200)

    @field_validator("title")
    @classmethod
    def nonblank_title(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("title cannot be blank")
        return value


def own_conversation(connection: psycopg.Connection, conversation_id: UUID,
                     identity: StudentAssertion) -> dict:
    row = connection.execute(
        "SELECT id, course_id, title, created_at, updated_at "
        "FROM ai_tutor.ai_conversations "
        "WHERE id = %s AND user_id = %s AND course_id = %s AND deleted_at IS NULL FOR SHARE",
        (conversation_id, identity.user_id, identity.course_id),
    ).fetchone()
    if row is None:
        raise AiTutorError("AI_CONVERSATION_NOT_FOUND", 404, "Conversation not found")
    return row


@router.post("/conversations", status_code=201)
def create_conversation(request: Request, body: CreateConversation,
                        identity: StudentAssertion = Depends(require_student_assertion)) -> dict:
    conversation_id = uuid4()
    with database(request) as connection:
        row = connection.execute(
            "INSERT INTO ai_tutor.ai_conversations (id, user_id, course_id, title) "
            "VALUES (%s, %s, %s, %s) "
            "RETURNING id, course_id, title, created_at, updated_at",
            (conversation_id, identity.user_id, identity.course_id, body.title),
        ).fetchone()
    return row


@router.get("/conversations")
def list_conversations(request: Request,
                       page: int = Query(default=1, ge=1),
                       page_size: int = Query(default=20, ge=1, le=100),
                       identity: StudentAssertion = Depends(require_student_assertion)) -> dict:
    with database(request) as connection:
        total = connection.execute(
            "SELECT count(*) AS total FROM ai_tutor.ai_conversations "
            "WHERE user_id = %s AND course_id = %s AND deleted_at IS NULL",
            (identity.user_id, identity.course_id),
        ).fetchone()["total"]
        items = connection.execute(
            "SELECT id, course_id, title, created_at, updated_at "
            "FROM ai_tutor.ai_conversations "
            "WHERE user_id = %s AND course_id = %s AND deleted_at IS NULL "
            "ORDER BY updated_at DESC, id DESC LIMIT %s OFFSET %s",
            (identity.user_id, identity.course_id, page_size, (page - 1) * page_size),
        ).fetchall()
    return {"items": items, "pagination": {
        "page": page, "page_size": page_size, "total": total,
        "has_next": page * page_size < total,
    }}


@router.get("/conversations/{conversationId}")
def get_conversation(request: Request, conversationId: UUID,
                     identity: StudentAssertion = Depends(require_student_assertion)) -> dict:
    with database(request) as connection:
        return own_conversation(connection, conversationId, identity)


@router.delete("/conversations/{conversationId}", status_code=204)
def delete_conversation(request: Request, conversationId: UUID,
                        identity: StudentAssertion = Depends(require_student_assertion)) -> None:
    with database(request) as connection:
        row = connection.execute(
            "UPDATE ai_tutor.ai_conversations SET deleted_at = now(), updated_at = now() "
            "WHERE id = %s AND user_id = %s AND course_id = %s AND deleted_at IS NULL "
            "RETURNING id",
            (conversationId, identity.user_id, identity.course_id),
        ).fetchone()
        if row is None:
            raise AiTutorError("AI_CONVERSATION_NOT_FOUND", 404, "Conversation not found")


@router.get("/conversations/{conversationId}/messages")
def list_messages(request: Request, conversationId: UUID,
                  page: int = Query(default=1, ge=1),
                  page_size: int = Query(default=20, ge=1, le=100),
                  identity: StudentAssertion = Depends(require_student_assertion)) -> dict:
    with database(request) as connection:
        own_conversation(connection, conversationId, identity)
        total = connection.execute(
            "SELECT count(*) AS total FROM ai_tutor.ai_messages WHERE conversation_id = %s",
            (conversationId,),
        ).fetchone()["total"]
        messages = connection.execute(
            "SELECT id, conversation_id, role, content, answer_status, created_at "
            "FROM ai_tutor.ai_messages WHERE conversation_id = %s "
            "ORDER BY created_at, id LIMIT %s OFFSET %s",
            (conversationId, page_size, (page - 1) * page_size),
        ).fetchall()
        ids = [message["id"] for message in messages]
        citations = connection.execute(
            "SELECT message_id, chunk_id, content_id, lesson_id, source_title, excerpt, "
            "version_number, page_number, heading, json_path FROM ai_tutor.ai_message_citations "
            "WHERE message_id = ANY(%s) ORDER BY message_id, ordinal",
            (ids,),
        ).fetchall() if ids else []
    by_message: dict[UUID, list[dict]] = {message_id: [] for message_id in ids}
    for citation in citations:
        message_id = citation.pop("message_id")
        by_message[message_id].append(citation)
    for message in messages:
        if message["role"] == "USER":
            message.pop("answer_status")
        message["citations"] = by_message[message["id"]]
    return {"items": messages, "pagination": {
        "page": page, "page_size": page_size, "total": total,
        "has_next": page * page_size < total,
    }}
