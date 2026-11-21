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
from oep_ai_tutor.retrieval import current_evidence
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
                        answer: str, *, answer_status: str = "ABSTAINED",
                        cited_chunk_ids: tuple[UUID, ...] = (),
                        readable_lesson_ids: tuple[UUID, ...] = (),
                        space_key: str = "", dimension: int = 0) -> dict:
    """Commit one user/assistant pair and the completion pointer in one transaction.

    ANSWERED citations are rebuilt from locked, current source rows in this transaction.
    """
    if (answer_status not in ("ABSTAINED", "ANSWERED") or not question.strip()
            or not answer.strip() or len(answer) > 20000 or len(cited_chunk_ids) > 20
            or len(set(cited_chunk_ids)) != len(cited_chunk_ids)):
        raise ValueError("Invalid chat turn or citation set")
    if answer_status == "ANSWERED" and (
            not cited_chunk_ids or not readable_lesson_ids or not space_key or dimension <= 0):
        raise ValueError("Grounded answer requires authorized citation scope")
    if answer_status == "ABSTAINED" and cited_chunk_ids:
        raise ValueError("Abstention cannot cite a source")
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
        cited = current_evidence(
            connection, chunk_ids=list(cited_chunk_ids), course_id=identity.course_id,
            lesson_ids=readable_lesson_ids, space_key=space_key, dimension=dimension,
            lock=True) if cited_chunk_ids else {}
        if len(cited) != len(cited_chunk_ids):
            raise AiTutorError("AI_INDEX_NOT_READY", 409, "Cited source is no longer available")
        user_message = connection.execute(
            "INSERT INTO ai_tutor.ai_messages (id, conversation_id, role, content, created_at) "
            "VALUES (%s, %s, 'USER', %s, "
            "greatest(clock_timestamp(), "
            "coalesce((SELECT max(created_at) + interval '1 microsecond' "
            "FROM ai_tutor.ai_messages WHERE conversation_id = %s), clock_timestamp()))) "
            "RETURNING id, conversation_id, role, content, created_at",
            (uuid4(), conversation_id, question.strip(), conversation_id),
        ).fetchone()
        assistant_message = connection.execute(
            "INSERT INTO ai_tutor.ai_messages "
            "(id, conversation_id, role, content, answer_status, created_at) "
            "VALUES (%s, %s, 'ASSISTANT', %s, %s, %s + interval '1 microsecond') "
            "RETURNING id, conversation_id, role, content, answer_status, created_at",
            (uuid4(), conversation_id, answer.strip(), answer_status,
             user_message["created_at"]),
        ).fetchone()
        citations = []
        for ordinal, chunk_id in enumerate(cited_chunk_ids):
            row = cited[chunk_id]
            citation = {"chunk_id": chunk_id, "content_id": row["content_id"],
                        "lesson_id": row["lesson_id"], "source_title": row["source_title"],
                        "excerpt": row["chunk_text"][:1000],
                        "version_number": row["version_number"],
                        "page_number": row["page_number"], "heading": row["heading"],
                        "json_path": row["json_path"]}
            connection.execute(
                "INSERT INTO ai_tutor.ai_message_citations "
                "(id, message_id, chunk_id, content_id, lesson_id, source_title, excerpt, "
                "version_number, page_number, heading, json_path, ordinal) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                (uuid4(), assistant_message["id"], chunk_id, citation["content_id"],
                 citation["lesson_id"], citation["source_title"], citation["excerpt"],
                 citation["version_number"], citation["page_number"], citation["heading"],
                 citation["json_path"], ordinal),
            )
            citations.append(citation)
        connection.execute(
            "UPDATE ai_tutor.ai_chat_requests "
            "SET status = 'COMPLETED', response_message_id = %s, updated_at = now() WHERE id = %s",
            (assistant_message["id"], claim.request_id),
        )
        connection.execute(
            "UPDATE ai_tutor.ai_conversations SET updated_at = now() WHERE id = %s",
            (conversation_id,),
        )
        return {"conversation_id": conversation_id,
                "user_message": {**user_message, "citations": []},
                "assistant_message": {**assistant_message, "citations": citations}}


def replay_chat_request(connection: psycopg.Connection, identity: StudentAssertion,
                        conversation_id: UUID, replay: ChatReplay, *,
                        readable_lesson_ids: tuple[UUID, ...] = (),
                        space_key: str = "", dimension: int = 0) -> dict:
    """Rebuild the saved 201 response after ownership was checked by the claim."""
    own_conversation(connection, conversation_id, identity)
    assistant = connection.execute(
        "SELECT id, conversation_id, role, content, answer_status, created_at "
        "FROM ai_tutor.ai_messages WHERE id = %s AND conversation_id = %s "
        "AND role = 'ASSISTANT'", (replay.assistant_message_id, conversation_id),
    ).fetchone()
    if assistant is None:
        raise AiTutorError("AI_TUTOR_UNAVAILABLE", 503, "Saved chat turn is unavailable")
    user = connection.execute(
        "SELECT id, conversation_id, role, content, created_at "
        "FROM ai_tutor.ai_messages WHERE conversation_id = %s AND role = 'USER' "
        "AND created_at < %s ORDER BY created_at DESC, id DESC LIMIT 1",
        (conversation_id, assistant["created_at"]),
    ).fetchone()
    if user is None:
        raise AiTutorError("AI_TUTOR_UNAVAILABLE", 503, "Saved chat turn is unavailable")
    citations = connection.execute(
        "SELECT chunk_id, content_id, lesson_id, source_title, excerpt, "
        "version_number, page_number, heading, json_path "
        "FROM ai_tutor.ai_message_citations WHERE message_id = %s ORDER BY ordinal",
        (assistant["id"],),
    ).fetchall()
    if citations:
        valid = current_evidence(
            connection, chunk_ids=[citation["chunk_id"] for citation in citations],
            course_id=identity.course_id, lesson_ids=readable_lesson_ids,
            space_key=space_key, dimension=dimension)
        if len(valid) != len(citations):
            raise AiTutorError("AI_INDEX_NOT_READY", 409, "Cited source is no longer available")
    return {"conversation_id": conversation_id,
            "user_message": {**user, "citations": []},
            "assistant_message": {**assistant, "citations": citations}}


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
