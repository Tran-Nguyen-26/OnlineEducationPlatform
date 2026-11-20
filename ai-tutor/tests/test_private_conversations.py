from __future__ import annotations

import os
from datetime import datetime, timezone
from uuid import UUID, uuid4

import jwt
import psycopg
import pytest
from fastapi.testclient import TestClient

from oep_ai_tutor.app import create_app
from oep_ai_tutor.migrate import migrate


SECRET = "test-only-service-secret-with-at-least-32-bytes"


def headers(user_id: UUID, course_id: UUID, request_id: UUID) -> dict[str, str]:
    now = int(datetime.now(timezone.utc).timestamp())
    assertion = jwt.encode({
        "iss": "oep-core-lms", "aud": "m7-ai-tutor", "scope": "ai:student",
        "sub": str(user_id), "course_id": str(course_id),
        "request_id": str(request_id), "jti": str(uuid4()),
        "iat": now, "nbf": now, "exp": now + 45,
    }, SECRET, algorithm="HS256")
    return {"Authorization": f"Bearer {assertion}", "X-Request-Id": str(request_id)}


@pytest.mark.integration
def test_private_conversations_enforce_owner_course_delete_and_message_scope(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    dsn = os.environ.get("AI_TUTOR_TEST_DATABASE_URL")
    if not dsn:
        pytest.skip("Set AI_TUTOR_TEST_DATABASE_URL to an isolated PostgreSQL database")
    migrate(dsn)
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    monkeypatch.setenv("AI_TUTOR_DATABASE_URL", dsn)
    owner, stranger = uuid4(), uuid4()
    course_id, other_course = uuid4(), uuid4()
    request_id = uuid4()
    path = f"/internal/v1/ai/courses/{course_id}/conversations"
    owner_headers = headers(owner, course_id, request_id)
    stranger_headers = headers(stranger, course_id, request_id)

    with TestClient(create_app()) as client:
        assert client.post(path, json={"title": "Hidden"}).status_code == 401
        assert client.post(path, headers=owner_headers,
                           json={"title": "Hidden", "user_id": str(stranger)}).status_code == 422
        first = client.post(path, headers=owner_headers, json={"title": "First"})
        second = client.post(path, headers=owner_headers, json={})
        assert first.status_code == 201
        assert second.status_code == 201
        first_id = UUID(first.json()["id"])
        assert first.json()["course_id"] == str(course_id)
        assert second.json()["title"] == "New chat"

        page = client.get(path + "?page=1&page_size=1", headers=owner_headers)
        assert page.status_code == 200
        assert page.json()["pagination"] == {
            "page": 1, "page_size": 1, "total": 2, "has_next": True,
        }
        assert client.get(path, headers=stranger_headers).json()["pagination"]["total"] == 0

        item_path = path + f"/{first_id}"
        messages_path = item_path + "/messages"
        for method, target in ((client.get, item_path), (client.delete, item_path),
                               (client.get, messages_path)):
            response = method(target, headers=stranger_headers)
            assert response.status_code == 404
            assert response.json()["error"]["code"] == "AI_CONVERSATION_NOT_FOUND"
            assert response.json()["error"]["request_id"] == str(request_id)
        wrong_course_path = f"/internal/v1/ai/courses/{other_course}/conversations/{first_id}"
        assert client.get(wrong_course_path, headers=headers(owner, other_course, request_id)).status_code == 404

        assert client.get(messages_path, headers=owner_headers).json()["items"] == []
        user_message_id, answer_id = uuid4(), uuid4()
        with psycopg.connect(dsn) as connection:
            connection.execute(
                "INSERT INTO ai_tutor.ai_messages (id, conversation_id, role, content, created_at) "
                "VALUES (%s, %s, 'USER', 'Question', '2026-01-01T00:00:00Z')",
                (user_message_id, first_id),
            )
            connection.execute(
                "INSERT INTO ai_tutor.ai_messages "
                "(id, conversation_id, role, content, answer_status, created_at) "
                "VALUES (%s, %s, 'ASSISTANT', 'Answer', 'ANSWERED', '2026-01-01T00:00:01Z')",
                (answer_id, first_id),
            )
            connection.execute(
                "INSERT INTO ai_tutor.ai_message_citations "
                "(id, message_id, chunk_id, content_id, lesson_id, source_title, excerpt, "
                "version_number, ordinal) VALUES (%s, %s, %s, %s, %s, 'Lesson', 'Evidence', 1, 0)",
                (uuid4(), answer_id, uuid4(), uuid4(), uuid4()),
            )
        messages = client.get(messages_path, headers=owner_headers).json()["items"]
        assert [item["role"] for item in messages] == ["USER", "ASSISTANT"]
        assert "answer_status" not in messages[0]
        assert messages[0]["citations"] == []
        assert messages[1]["answer_status"] == "ANSWERED"
        assert messages[1]["citations"][0]["excerpt"] == "Evidence"

        assert client.delete(item_path, headers=owner_headers).status_code == 204
        assert client.get(item_path, headers=owner_headers).status_code == 404
        assert client.get(messages_path, headers=owner_headers).status_code == 404
        assert client.get(path, headers=owner_headers).json()["pagination"]["total"] == 1


def test_private_conversations_fail_closed_without_database(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    monkeypatch.delenv("AI_TUTOR_DATABASE_URL", raising=False)
    user_id, course_id, request_id = uuid4(), uuid4(), uuid4()
    with TestClient(create_app()) as client:
        response = client.get(
            f"/internal/v1/ai/courses/{course_id}/conversations",
            headers=headers(user_id, course_id, request_id),
        )
    assert response.status_code == 503
    assert response.json()["error"]["code"] == "AI_TUTOR_UNAVAILABLE"
