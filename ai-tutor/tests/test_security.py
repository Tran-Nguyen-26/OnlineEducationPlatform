from __future__ import annotations

from datetime import datetime, timezone
from hashlib import sha256
from uuid import UUID, uuid4

import jwt
import pytest
from fastapi import Depends
from fastapi.testclient import TestClient

from oep_ai_tutor.app import create_app
from oep_ai_tutor.security import (
    AUDIENCE,
    ISSUER,
    AssertionVerifier,
    InvalidServiceAssertion,
    StudentAssertion,
    require_signed_lesson_scope,
    require_student_assertion,
)


SECRET = "test-only-service-secret-with-at-least-32-bytes"
USER_ID = uuid4()
COURSE_ID = uuid4()
REQUEST_ID = uuid4()


def signed_claims(**changes: object) -> dict[str, object]:
    now = int(datetime.now(timezone.utc).timestamp())
    claims: dict[str, object] = {
        "iss": ISSUER,
        "aud": AUDIENCE,
        "sub": str(USER_ID),
        "iat": now,
        "nbf": now,
        "exp": now + 45,
        "jti": str(uuid4()),
        "course_id": str(COURSE_ID),
        "request_id": str(REQUEST_ID),
        "scope": "ai:student",
    }
    claims.update(changes)
    return claims


def token(**changes: object) -> str:
    return jwt.encode(signed_claims(**changes), SECRET, algorithm="HS256")


def test_valid_assertion_is_bound_to_verified_user_course_and_request() -> None:
    result = AssertionVerifier(SECRET).verify_student(
        token(), course_id=COURSE_ID, request_id=REQUEST_ID
    )
    assert result.user_id == USER_ID
    assert result.course_id == COURSE_ID
    assert result.request_id == REQUEST_ID


def test_signed_lesson_scope_matches_java_canonical_digest() -> None:
    first = "11111111-1111-4111-8111-111111111111"
    second = "22222222-2222-4222-8222-222222222222"
    digest = "a22ad13a7d741402a16b943ba95a73481dfdbcece26d55a1e987bc81bec9b89b"
    assertion = AssertionVerifier(SECRET).verify_student(
        token(lesson_scope_sha256=digest), course_id=COURSE_ID, request_id=REQUEST_ID
    )
    assert assertion.require_lesson_scope([second, first]) == (UUID(second), UUID(first))
    assert digest == sha256(f"{first}\n{second}".encode()).hexdigest()

    for invalid in ([first], [first, first, second], ["AAAAAAAA-AAAA-4AAA-8AAA-AAAAAAAAAAAA", second]):
        with pytest.raises(InvalidServiceAssertion):
            assertion.require_lesson_scope(invalid)

    unscoped = AssertionVerifier(SECRET).verify_student(
        token(), course_id=COURSE_ID, request_id=REQUEST_ID
    )
    with pytest.raises(InvalidServiceAssertion):
        unscoped.require_lesson_scope([first, second])


def test_malformed_lesson_scope_digest_is_rejected() -> None:
    for invalid in ("wrong", "A" * 64, 123):
        with pytest.raises(InvalidServiceAssertion):
            AssertionVerifier(SECRET).verify_student(
                token(lesson_scope_sha256=invalid), course_id=COURSE_ID, request_id=REQUEST_ID
            )


def test_private_handler_rejects_a_tampered_lesson_list(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    app = create_app()

    def probe(body: dict[str, list[str]], identity: StudentAssertion = Depends(require_student_assertion)) -> dict[str, int]:
        return {"count": len(require_signed_lesson_scope(identity, body["readable_lesson_ids"]))}

    app.add_api_route("/internal/v1/ai/courses/{courseId}/_scope_probe", probe, methods=["POST"])
    first = "11111111-1111-4111-8111-111111111111"
    second = "22222222-2222-4222-8222-222222222222"
    headers = {
        "Authorization": f"Bearer {token(lesson_scope_sha256='a22ad13a7d741402a16b943ba95a73481dfdbcece26d55a1e987bc81bec9b89b')}",
        "X-Request-Id": str(REQUEST_ID),
    }
    path = f"/internal/v1/ai/courses/{COURSE_ID}/_scope_probe"
    with TestClient(app) as client:
        assert client.post(path, headers=headers, json={"readable_lesson_ids": [first, second]}).json() == {"count": 2}
        assert client.post(path, headers=headers, json={"readable_lesson_ids": [first]}).status_code == 401


@pytest.mark.parametrize(
    "changes",
    [
        {"iss": "attacker"},
        {"aud": "another-service"},
        {"scope": "ai:index"},
        {"course_id": str(uuid4())},
        {"request_id": str(uuid4())},
        {"sub": "not-a-uuid"},
        {"jti": "not-a-uuid"},
        {"exp": 1},
        {"exp": int(datetime.now(timezone.utc).timestamp()) + 600},
    ],
)
def test_invalid_claims_are_rejected(changes: dict[str, object]) -> None:
    with pytest.raises(InvalidServiceAssertion):
        AssertionVerifier(SECRET).verify_student(
            token(**changes), course_id=COURSE_ID, request_id=REQUEST_ID
        )


def test_missing_claim_and_wrong_signature_are_rejected() -> None:
    claims = signed_claims()
    claims.pop("course_id")
    verifier = AssertionVerifier(SECRET)
    for bad_token in (
        jwt.encode(claims, SECRET, algorithm="HS256"),
        jwt.encode(signed_claims(), "a-different-secret-with-at-least-32-bytes", algorithm="HS256"),
    ):
        with pytest.raises(InvalidServiceAssertion):
            verifier.verify_student(bad_token, course_id=COURSE_ID, request_id=REQUEST_ID)


def test_http_dependency_rejects_browser_token_and_accepts_internal_token(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    app = create_app()

    def probe(identity: StudentAssertion = Depends(require_student_assertion)) -> dict[str, UUID]:
        return {"user_id": identity.user_id}

    app.add_api_route("/internal/v1/ai/courses/{courseId}/_probe", probe, methods=["GET"])
    with TestClient(app) as client:
        path = f"/internal/v1/ai/courses/{COURSE_ID}/_probe"
        assert client.get(path).status_code == 401
        assert client.get(path, headers={"Authorization": "Bearer public-user-token"}).status_code == 401
        assert client.get(
            path,
            headers={"Authorization": f"Bearer {token()}", "X-Request-Id": str(REQUEST_ID)},
        ).json() == {"user_id": str(USER_ID)}
        assert client.get(
            path,
            headers={"Authorization": f"Bearer {token()}", "X-Request-Id": str(uuid4())},
        ).status_code == 401


def test_health_endpoint_reports_service_available(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    with TestClient(create_app()) as client:
        response = client.get("/healthz")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_missing_or_short_service_secret_fails_startup(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("AI_TUTOR_SERVICE_JWT_SECRET", raising=False)
    with pytest.raises(ValueError):
        create_app()
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", "short")
    with pytest.raises(ValueError):
        create_app()
