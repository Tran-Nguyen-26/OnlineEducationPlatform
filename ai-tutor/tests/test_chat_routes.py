"""Private chat turns require signed lesson scope and current cited chunks."""

from __future__ import annotations

from datetime import datetime, timezone
import os
from uuid import UUID, uuid4

import jwt
import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo
import pytest
from fastapi.testclient import TestClient

from oep_ai_tutor import chat_routes
from oep_ai_tutor.app import create_app
from oep_ai_tutor.generation import GenerationResult
from oep_ai_tutor.migrate import migrate
from oep_ai_tutor.pgvector_store import PgvectorStore
from oep_ai_tutor.source_client import SourceSnapshot
from oep_ai_tutor.source_client import SourceUnavailable
from oep_ai_tutor.vector_store import VectorRecord


SECRET = "chat-route-service-secret-longer-than-32-bytes"
SPACE = "chat-test:3"


class FakeEmbedding:
    dimension = 3

    def embed_query(self, query: str) -> list[float]:
        return [1.0, 0.0, 0.0]

    def embed_passages(self, passages: list[str]) -> list[list[float]]:
        return [[1.0, 0.0, 0.0] for _ in passages]


class FakeGeneration:
    model = "fake"
    answer = "The source states the answer. [1]"

    def generate(self, instructions, messages):
        assert "untrusted" in instructions.lower()
        assert "Untrusted source excerpts:" in messages[-1].content
        return GenerationResult(self.answer, 10, 12, self.model)


@pytest.fixture(scope="module")
def chat_dsn():
    base = os.environ.get("AI_TUTOR_TEST_DATABASE_URL")
    if not base:
        pytest.skip("Set AI_TUTOR_TEST_DATABASE_URL to an isolated pgvector database")
    name = "oep_ai_chat_route_" + uuid4().hex
    with psycopg.connect(base, autocommit=True) as admin:
        admin.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(name)))
    try:
        dsn = make_conninfo(base, dbname=name)
        assert migrate(dsn) == [1, 2]
        yield dsn
    finally:
        with psycopg.connect(base, autocommit=True) as admin:
            admin.execute(sql.SQL("DROP DATABASE {} WITH (FORCE)").format(sql.Identifier(name)))


def _headers(user: UUID, course: UUID, lessons: tuple[UUID, ...], key: UUID) -> dict:
    now = int(datetime.now(timezone.utc).timestamp())
    from hashlib import sha256
    scope = sha256("\n".join(sorted(str(lesson) for lesson in lessons)).encode()).hexdigest()
    request_id = uuid4()
    token = jwt.encode({"iss": "oep-core-lms", "aud": "m7-ai-tutor", "scope": "ai:student",
                        "sub": str(user), "course_id": str(course), "request_id": str(request_id),
                        "jti": str(uuid4()), "iat": now, "nbf": now, "exp": now + 45,
                        "lesson_scope_sha256": scope}, SECRET, algorithm="HS256")
    return {"Authorization": "Bearer " + token, "X-Request-Id": str(request_id),
            "Idempotency-Key": str(key)}


@pytest.mark.integration
def test_grounded_turn_replay_abstention_and_revocation(
    chat_dsn: str, monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("AI_TUTOR_DATABASE_URL", chat_dsn)
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    monkeypatch.setenv("AI_TUTOR_EMBEDDING_SPACE_KEY", SPACE)
    monkeypatch.setenv("AI_TUTOR_M2_BASE_URL", "http://m2.internal")
    monkeypatch.setattr(chat_routes, "embedding_provider_from_env", lambda _: FakeEmbedding())
    monkeypatch.setattr(chat_routes, "generation_provider_from_env", lambda _: FakeGeneration())
    monkeypatch.setattr(chat_routes, "vector_store_from_env",
                        lambda **_: PgvectorStore(chat_dsn))
    monkeypatch.setattr(chat_routes, "fetch_source", lambda event, **_: SourceSnapshot(
        event.content_id, event.course_id, event.lesson_id, event.version_number,
        "TEXT", "Lesson", "The source states the answer.", None, None, None, None))
    user, course, lesson, content, conversation, revision, chunk = (uuid4() for _ in range(7))
    path = f"/internal/v1/ai/courses/{course}/conversations/{conversation}/messages"
    with psycopg.connect(chat_dsn) as connection:
        connection.execute("INSERT INTO ai_tutor.ai_conversations "
                           "(id, user_id, course_id, title) VALUES (%s, %s, %s, 'Chat')",
                           (conversation, user, course))
    with TestClient(create_app()) as client:
        headers = _headers(user, course, (lesson,), uuid4())
        body = {"message": "What is stated?", "readable_lesson_ids": [str(lesson)]}
        assert client.post(path, headers=headers, json=body).status_code == 409
        with psycopg.connect(chat_dsn) as connection:
            connection.execute(
                "INSERT INTO ai_tutor.ai_source_revisions "
                "(id, content_id, version_number, course_id, lesson_id, content_type, "
                "source_title, state, embedding_space_key) "
                "VALUES (%s, %s, 1, %s, %s, 'TEXT', 'Lesson', 'READY', %s)",
                (revision, content, course, lesson, SPACE),
            )
            connection.execute(
                "INSERT INTO ai_tutor.ai_source_chunks "
                "(id, source_revision_id, course_id, lesson_id, chunk_index, chunk_text, "
                "token_count, embedding_space_key, embedding_dimension) "
                "VALUES (%s, %s, %s, %s, 0, 'The source states the answer.', 6, %s, 3)",
                (chunk, revision, course, lesson, SPACE),
            )
        PgvectorStore(chat_dsn).upsert(VectorRecord(
            chunk, revision, course, lesson, SPACE, [1.0, 0.0, 0.0]))
        assert client.post(path, headers=headers,
                           json={**body, "readable_lesson_ids": []}).status_code == 401
        assert client.post(path, headers=_headers(uuid4(), course, (lesson,), uuid4()),
                           json=body).status_code == 404
        answered = client.post(path, headers=headers, json=body)
        assert answered.status_code == 201, answered.text
        result = answered.json()
        assert result["assistant_message"]["answer_status"] == "ANSWERED"
        assert result["assistant_message"]["citations"][0]["chunk_id"] == str(chunk)
        assert result["user_message"]["citations"] == []
        replay = client.post(path, headers=headers, json=body)
        assert replay.status_code == 201 and replay.json() == result
        conflict = client.post(path, headers=headers,
                               json={**body, "message": "Different question"})
        assert conflict.status_code == 409
        assert conflict.json()["error"]["code"] == "IDEMPOTENCY_CONFLICT"
        no_lesson = client.post(path, headers=_headers(user, course, (), uuid4()),
                                json={**body, "readable_lesson_ids": []})
        assert no_lesson.status_code == 201
        assert no_lesson.json()["assistant_message"]["answer_status"] == "ABSTAINED"
        monkeypatch.setattr(FakeGeneration, "answer", "Unsupported claim without a citation.")
        unsupported = client.post(path, headers=_headers(user, course, (lesson,), uuid4()),
                                  json=body)
        assert unsupported.status_code == 201
        assert unsupported.json()["assistant_message"]["answer_status"] == "ABSTAINED"
        monkeypatch.setattr(chat_routes, "fetch_source",
                            lambda *_, **__: (_ for _ in ()).throw(SourceUnavailable()))
        unavailable = client.post(path, headers=_headers(user, course, (lesson,), uuid4()),
                                  json=body)
        assert unavailable.status_code == 409
        assert unavailable.json()["error"]["code"] == "AI_INDEX_NOT_READY"
        assert client.post(path, headers=headers, json=body).status_code == 409
        with psycopg.connect(chat_dsn) as connection:
            connection.execute("UPDATE ai_tutor.ai_source_revisions "
                               "SET state = 'INACTIVE', deactivated_at = now() WHERE id = %s",
                               (revision,))
        stale = client.post(path, headers=headers, json=body)
        assert stale.status_code == 409
        with psycopg.connect(chat_dsn) as connection:
            count = connection.execute(
                "SELECT count(*) FROM ai_tutor.ai_messages WHERE conversation_id = %s",
                (conversation,),
            ).fetchone()[0]
        assert count == 6
