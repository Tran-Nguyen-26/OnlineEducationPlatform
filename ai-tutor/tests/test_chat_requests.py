"""Durable chat claim/replay and atomic abstention pair tests on PostgreSQL."""

from __future__ import annotations

import os
from uuid import uuid4

import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo
from psycopg.rows import dict_row
import pytest

from oep_ai_tutor.chat_requests import (
    ChatClaim,
    ChatReplay,
    claim_chat_request,
    fail_chat_request,
    finish_chat_request,
    normalized_payload_hash,
)
from oep_ai_tutor.errors import AiTutorError
from oep_ai_tutor.migrate import migrate
from oep_ai_tutor.security import StudentAssertion


@pytest.fixture(scope="module")
def ledger_dsn():
    base_dsn = os.environ.get("AI_TUTOR_TEST_DATABASE_URL")
    if not base_dsn:
        pytest.skip("Set AI_TUTOR_TEST_DATABASE_URL to an isolated PostgreSQL database")
    database_name = "oep_ai_ledger_" + uuid4().hex
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


def test_payload_hash_normalizes_whitespace_and_scope_order() -> None:
    first, second = uuid4(), uuid4()
    assert normalized_payload_hash("  Question  ", (first, second)) == normalized_payload_hash(
        "Question", (second, first))
    assert normalized_payload_hash("Another", (first, second)) != normalized_payload_hash(
        "Question", (first, second))


@pytest.mark.integration
def test_claim_retry_atomic_completion_and_replay(ledger_dsn: str) -> None:
    identity = StudentAssertion(uuid4(), uuid4(), uuid4(), uuid4(), None)
    foreign = StudentAssertion(uuid4(), identity.course_id, uuid4(), uuid4(), None)
    conversation_id, key = uuid4(), uuid4()
    payload_hash = normalized_payload_hash("Question", ())
    with psycopg.connect(ledger_dsn, autocommit=True, row_factory=dict_row) as connection:
        connection.execute(
            "INSERT INTO ai_tutor.ai_conversations (id, user_id, course_id, title) "
            "VALUES (%s, %s, %s, 'Chat')",
            (conversation_id, identity.user_id, identity.course_id),
        )
        with pytest.raises(AiTutorError) as denied:
            claim_chat_request(connection, foreign, conversation_id, key, payload_hash)
        assert (denied.value.code, denied.value.status) == ("AI_CONVERSATION_NOT_FOUND", 404)

        first = claim_chat_request(connection, identity, conversation_id, key, payload_hash)
        assert first == ChatClaim(first.request_id, 1)
        with pytest.raises(AiTutorError) as conflict:
            claim_chat_request(connection, identity, conversation_id, key,
                               normalized_payload_hash("Different", ()))
        assert conflict.value.code == "IDEMPOTENCY_CONFLICT"
        with pytest.raises(AiTutorError) as pending:
            claim_chat_request(connection, identity, conversation_id, key, payload_hash)
        assert pending.value.code == "AI_REQUEST_IN_PROGRESS"

        fail_chat_request(connection, identity, conversation_id, first, retryable=True)
        second = claim_chat_request(connection, identity, conversation_id, key, payload_hash)
        assert second == ChatClaim(first.request_id, 2)
        with pytest.raises(AiTutorError) as stale:
            finish_chat_request(connection, identity, conversation_id, first,
                                "Question", "No evidence")
        assert stale.value.code == "AI_REQUEST_IN_PROGRESS"

        result = finish_chat_request(connection, identity, conversation_id, second,
                                     "Question", "No evidence")
        assert result["assistant_message"]["answer_status"] == "ABSTAINED"
        assert result["user_message"]["created_at"] < result["assistant_message"]["created_at"]
        replay = claim_chat_request(connection, identity, conversation_id, key, payload_hash)
        assert replay == ChatReplay(result["assistant_message"]["id"])
        count = connection.execute(
            "SELECT count(*) AS count FROM ai_tutor.ai_messages WHERE conversation_id = %s",
            (conversation_id,),
        ).fetchone()["count"]
        assert count == 2
        with pytest.raises(AiTutorError):
            finish_chat_request(connection, identity, conversation_id, second,
                                "Question", "No evidence")


@pytest.mark.integration
def test_stale_claim_is_fenced_and_deleted_conversation_cannot_replay(ledger_dsn: str) -> None:
    identity = StudentAssertion(uuid4(), uuid4(), uuid4(), uuid4(), None)
    conversation_id, key = uuid4(), uuid4()
    payload_hash = normalized_payload_hash("Question", ())
    with psycopg.connect(ledger_dsn, autocommit=True, row_factory=dict_row) as connection:
        connection.execute(
            "INSERT INTO ai_tutor.ai_conversations (id, user_id, course_id, title) "
            "VALUES (%s, %s, %s, 'Chat')",
            (conversation_id, identity.user_id, identity.course_id),
        )
        first = claim_chat_request(connection, identity, conversation_id, key, payload_hash)
        connection.execute(
            "UPDATE ai_tutor.ai_chat_requests "
            "SET updated_at = now() - interval '61 seconds' WHERE id = %s",
            (first.request_id,),
        )
        second = claim_chat_request(connection, identity, conversation_id, key, payload_hash)
        assert second.attempt == 2
        with pytest.raises(AiTutorError):
            fail_chat_request(connection, identity, conversation_id, first, retryable=True)
        finish_chat_request(connection, identity, conversation_id, second,
                            "Question", "No evidence")
        connection.execute(
            "UPDATE ai_tutor.ai_conversations SET deleted_at = now() WHERE id = %s",
            (conversation_id,),
        )
        with pytest.raises(AiTutorError) as denied:
            claim_chat_request(connection, identity, conversation_id, key, payload_hash)
        assert denied.value.code == "AI_CONVERSATION_NOT_FOUND"
