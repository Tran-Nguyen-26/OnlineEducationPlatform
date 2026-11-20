"""Durable idempotency claims and atomic, citation-free chat turn persistence.

Callers must validate the request and current student access before claim or replay.
Grounded answer citation validation is a separate send-turn integration step.
"""

from __future__ import annotations

from dataclasses import dataclass
from hashlib import sha256
import json
from uuid import UUID, uuid4

import psycopg

from oep_ai_tutor.conversations import own_conversation
from oep_ai_tutor.errors import AiTutorError
from oep_ai_tutor.security import StudentAssertion


@dataclass(frozen=True)
class ChatClaim:
    request_id: UUID
    attempt: int


@dataclass(frozen=True)
class ChatReplay:
    assistant_message_id: UUID


def normalized_payload_hash(message: str, readable_lesson_ids: tuple[UUID, ...]) -> str:
    if not isinstance(message, str) or not message.strip():
        raise ValueError("Message must be nonblank")
    canonical = {"message": message.strip(),
                 "readable_lesson_ids": sorted(str(value) for value in readable_lesson_ids)}
    return sha256(json.dumps(canonical, sort_keys=True, separators=(",", ":"),
                             ensure_ascii=False).encode("utf-8")).hexdigest()


def claim_chat_request(connection: psycopg.Connection, identity: StudentAssertion,
                       conversation_id: UUID, key: UUID, payload_hash: str) -> ChatClaim | ChatReplay:
    """Claim a key, replay a committed answer, or fail closed on conflicting/in-flight use."""
    if key.version != 4 or len(payload_hash) != 64 or any(c not in "0123456789abcdef" for c in payload_hash):
        raise ValueError("Invalid idempotency key or payload hash")
    with connection.transaction():
        own_conversation(connection, conversation_id, identity)
        inserted = connection.execute(
            "INSERT INTO ai_tutor.ai_chat_requests "
            "(id, user_id, conversation_id, idempotency_key, payload_hash, status, attempt_count) "
            "VALUES (%s, %s, %s, %s, %s, 'PROCESSING', 1) "
            "ON CONFLICT (user_id, conversation_id, idempotency_key) DO NOTHING "
            "RETURNING id, attempt_count",
            (uuid4(), identity.user_id, conversation_id, key, payload_hash),
        ).fetchone()
        if inserted is not None:
            return ChatClaim(inserted["id"], inserted["attempt_count"])
        row = connection.execute(
            "SELECT id, payload_hash, status, response_message_id, attempt_count, "
            "updated_at < now() - interval '60 seconds' AS stale "
            "FROM ai_tutor.ai_chat_requests "
            "WHERE user_id = %s AND conversation_id = %s AND idempotency_key = %s FOR UPDATE",
            (identity.user_id, conversation_id, key),
        ).fetchone()
        if row["payload_hash"] != payload_hash:
            raise AiTutorError("IDEMPOTENCY_CONFLICT", 409, "Idempotency key has a different payload")
        if row["status"] == "COMPLETED":
            return ChatReplay(row["response_message_id"])
        if row["status"] == "FAILED_FINAL":
            raise AiTutorError("AI_SERVICE_UNAVAILABLE", 503, "Chat request failed permanently")
        if row["status"] == "PROCESSING" and not row["stale"]:
            raise AiTutorError("AI_REQUEST_IN_PROGRESS", 409, "Chat request is in progress")
        resumed = connection.execute(
            "UPDATE ai_tutor.ai_chat_requests "
            "SET status = 'PROCESSING', attempt_count = attempt_count + 1, updated_at = now() "
            "WHERE id = %s RETURNING id, attempt_count",
            (row["id"],),
        ).fetchone()
        return ChatClaim(resumed["id"], resumed["attempt_count"])


def finish_chat_request(connection: psycopg.Connection, identity: StudentAssertion,
                        conversation_id: UUID, claim: ChatClaim, question: str,
                        answer: str, *, answer_status: str = "ABSTAINED") -> dict:
    """Commit one user/assistant pair and the completion pointer in one transaction.

    Until citation validation is integrated, only citation-free abstentions are allowed.
    """
    if answer_status != "ABSTAINED" or not question.strip() or not answer.strip():
        raise ValueError("Only nonblank citation-free abstentions can be persisted here")
    with connection.transaction():
        conversation = connection.execute(
            "SELECT id FROM ai_tutor.ai_conversations "
            "WHERE id = %s AND user_id = %s AND course_id = %s AND deleted_at IS NULL FOR UPDATE",
            (conversation_id, identity.user_id, identity.course_id),
        ).fetchone()
        if conversation is None:
            raise AiTutorError("AI_CONVERSATION_NOT_FOUND", 404, "Conversation not found")
        request = connection.execute(
            "SELECT status, attempt_count FROM ai_tutor.ai_chat_requests "
            "WHERE id = %s AND user_id = %s AND conversation_id = %s FOR UPDATE",
            (claim.request_id, identity.user_id, conversation_id),
        ).fetchone()
        if request is None or request["status"] != "PROCESSING" or request["attempt_count"] != claim.attempt:
            raise AiTutorError("AI_REQUEST_IN_PROGRESS", 409, "Chat request claim is no longer active")
        user_message = connection.execute(
            "INSERT INTO ai_tutor.ai_messages (id, conversation_id, role, content, created_at) "
            "VALUES (%s, %s, 'USER', %s, "
            "greatest(clock_timestamp(), "
            "coalesce((SELECT max(created_at) + interval '1 microsecond' "
            "FROM ai_tutor.ai_messages WHERE conversation_id = %s), clock_timestamp()))) "
            "RETURNING id, role, content, created_at",
            (uuid4(), conversation_id, question.strip(), conversation_id),
        ).fetchone()
        assistant_message = connection.execute(
            "INSERT INTO ai_tutor.ai_messages "
            "(id, conversation_id, role, content, answer_status, created_at) "
            "VALUES (%s, %s, 'ASSISTANT', %s, 'ABSTAINED', %s + interval '1 microsecond') "
            "RETURNING id, role, content, answer_status, created_at",
            (uuid4(), conversation_id, answer.strip(), user_message["created_at"]),
        ).fetchone()
        connection.execute(
            "UPDATE ai_tutor.ai_chat_requests "
            "SET status = 'COMPLETED', response_message_id = %s, updated_at = now() WHERE id = %s",
            (assistant_message["id"], claim.request_id),
        )
        connection.execute(
            "UPDATE ai_tutor.ai_conversations SET updated_at = now() WHERE id = %s",
            (conversation_id,),
        )
        return {"conversation_id": conversation_id, "user_message": user_message,
                "assistant_message": {**assistant_message, "citations": []}}


def fail_chat_request(connection: psycopg.Connection, identity: StudentAssertion,
                      conversation_id: UUID, claim: ChatClaim, *, retryable: bool) -> None:
    """Release a failed attempt for the same-key retry, fenced by attempt number."""
    with connection.transaction():
        updated = connection.execute(
            "UPDATE ai_tutor.ai_chat_requests "
            "SET status = %s, updated_at = now() "
            "WHERE id = %s AND user_id = %s AND conversation_id = %s "
            "AND status = 'PROCESSING' AND attempt_count = %s RETURNING id",
            ("FAILED_RETRYABLE" if retryable else "FAILED_FINAL", claim.request_id,
             identity.user_id, conversation_id, claim.attempt),
        ).fetchone()
        if updated is None:
            raise AiTutorError("AI_REQUEST_IN_PROGRESS", 409, "Chat request claim is no longer active")
