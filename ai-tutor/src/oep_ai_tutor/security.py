"""Verification for short-lived assertions issued by the Spring Boot service."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from hashlib import sha256
import hmac
import re
from uuid import UUID

import jwt
from fastapi import HTTPException, Request, status


ISSUER = "oep-core-lms"
AUDIENCE = "m7-ai-tutor"
STUDENT_SCOPE = "ai:student"
INDEX_SCOPE = "ai:index"
MAX_TTL_SECONDS = 60
CLOCK_LEEWAY_SECONDS = 3


@dataclass(frozen=True)
class StudentAssertion:
    user_id: UUID
    course_id: UUID
    request_id: UUID
    token_id: UUID
    lesson_scope_sha256: str | None

    def require_lesson_scope(self, lesson_ids: list[str]) -> tuple[UUID, ...]:
        """Verify the private chat body's lesson set against the signed assertion."""
        if self.lesson_scope_sha256 is None or not isinstance(lesson_ids, list):
            raise InvalidServiceAssertion("Missing signed lesson scope")
        try:
            parsed = tuple(UUID(item) for item in lesson_ids)
            if any(str(value) != raw for value, raw in zip(parsed, lesson_ids)):
                raise ValueError("Lesson IDs must use canonical UUID spelling")
            if len(set(parsed)) != len(parsed):
                raise ValueError("Duplicate lesson ID")
            canonical = "\n".join(sorted(str(value) for value in parsed))
            digest = sha256(canonical.encode("utf-8")).hexdigest()
            if not hmac.compare_digest(digest, self.lesson_scope_sha256):
                raise ValueError("Lesson scope does not match assertion")
            return parsed
        except (ValueError, TypeError, AttributeError) as exc:
            raise InvalidServiceAssertion("Invalid signed lesson scope") from exc


class InvalidServiceAssertion(ValueError):
    """The caller did not present a valid internal student assertion."""


class AssertionVerifier:
    def __init__(self, secret: str):
        if len(secret.encode("utf-8")) < 32:
            raise ValueError("AI_TUTOR_SERVICE_JWT_SECRET must contain at least 32 bytes")
        self._secret = secret

    def verify_student(self, token: str, *, course_id: UUID, request_id: UUID) -> StudentAssertion:
        try:
            claims = jwt.decode(
                token,
                self._secret,
                algorithms=["HS256"],
                issuer=ISSUER,
                audience=AUDIENCE,
                leeway=CLOCK_LEEWAY_SECONDS,
                options={
                    "require": [
                        "iss", "aud", "sub", "iat", "nbf", "exp", "jti",
                        "course_id", "request_id", "scope",
                    ]
                },
            )
            now = datetime.now(timezone.utc).timestamp()
            issued_at = claims["iat"]
            not_before = claims["nbf"]
            expires_at = claims["exp"]
            if not all(isinstance(value, int) and not isinstance(value, bool)
                       for value in (issued_at, not_before, expires_at)):
                raise ValueError("Assertion timestamps must be integer seconds")
            if not (0 < expires_at - issued_at <= MAX_TTL_SECONDS):
                raise ValueError("Assertion lifetime exceeds the contract")
            if issued_at > now + CLOCK_LEEWAY_SECONDS or not_before < issued_at:
                raise ValueError("Assertion time window is invalid")
            if claims["scope"] != STUDENT_SCOPE:
                raise ValueError("Assertion scope is not student access")
            user_id = UUID(claims["sub"])
            asserted_course_id = UUID(claims["course_id"])
            asserted_request_id = UUID(claims["request_id"])
            token_id = UUID(claims["jti"])
            if asserted_course_id != course_id or asserted_request_id != request_id:
                raise ValueError("Assertion does not match the request")
            lesson_scope_sha256 = claims.get("lesson_scope_sha256")
            if lesson_scope_sha256 is not None and (
                not isinstance(lesson_scope_sha256, str)
                or re.fullmatch(r"[0-9a-f]{64}", lesson_scope_sha256) is None
            ):
                raise ValueError("Malformed lesson scope digest")
            return StudentAssertion(user_id, course_id, request_id, token_id, lesson_scope_sha256)
        except (jwt.PyJWTError, ValueError, TypeError, KeyError) as exc:
            raise InvalidServiceAssertion("Invalid internal service assertion") from exc

    def verify_indexer(self, token: str, *, request_id: UUID) -> UUID:
        """Indexing uses a service scope, never a student/course assertion."""
        try:
            claims = jwt.decode(
                token, self._secret, algorithms=["HS256"], issuer=ISSUER,
                audience=AUDIENCE, leeway=CLOCK_LEEWAY_SECONDS,
                options={"require": ["iss", "aud", "sub", "iat", "nbf", "exp",
                                     "jti", "request_id", "scope"]},
            )
            now = datetime.now(timezone.utc).timestamp()
            issued_at, not_before, expires_at = (claims[key] for key in ("iat", "nbf", "exp"))
            if not all(type(value) is int for value in (issued_at, not_before, expires_at)):
                raise ValueError("Invalid assertion timestamps")
            if not (0 < expires_at - issued_at <= MAX_TTL_SECONDS):
                raise ValueError("Invalid assertion lifetime")
            if issued_at > now + CLOCK_LEEWAY_SECONDS or not_before < issued_at:
                raise ValueError("Invalid assertion time window")
            if claims["scope"] != INDEX_SCOPE or claims["sub"] != "oep-core-lms":
                raise ValueError("Invalid index service identity")
            if UUID(claims["request_id"]) != request_id:
                raise ValueError("Assertion request mismatch")
            UUID(claims["jti"])
            return request_id
        except (jwt.PyJWTError, ValueError, TypeError, KeyError) as exc:
            raise InvalidServiceAssertion("Invalid internal index assertion") from exc


def require_student_assertion(request: Request, courseId: UUID) -> StudentAssertion:
    """FastAPI dependency for private course-scoped student routes."""
    authorization = request.headers.get("authorization", "")
    scheme, _, token = authorization.partition(" ")
    try:
        request_id = UUID(request.headers.get("x-request-id", ""))
        if scheme.lower() != "bearer" or not token or token.strip() != token:
            raise InvalidServiceAssertion("Missing bearer assertion")
        return request.app.state.assertion_verifier.verify_student(
            token, course_id=courseId, request_id=request_id
        )
    except (InvalidServiceAssertion, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal service assertion",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc


def require_index_assertion(request: Request) -> UUID:
    authorization = request.headers.get("authorization", "")
    scheme, _, token = authorization.partition(" ")
    try:
        request_id = UUID(request.headers.get("x-request-id", ""))
        if scheme.lower() != "bearer" or not token or token.strip() != token:
            raise InvalidServiceAssertion("Missing bearer assertion")
        return request.app.state.assertion_verifier.verify_indexer(
            token, request_id=request_id)
    except (InvalidServiceAssertion, ValueError) as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal service assertion",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc


def require_signed_lesson_scope(assertion: StudentAssertion, lesson_ids: list[str]) -> tuple[UUID, ...]:
    """HTTP boundary for the private send-message handler before source retrieval."""
    try:
        return assertion.require_lesson_scope(lesson_ids)
    except InvalidServiceAssertion as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid internal service assertion",
            headers={"WWW-Authenticate": "Bearer"},
        ) from exc
