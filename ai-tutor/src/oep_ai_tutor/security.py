"""Verification for short-lived assertions issued by the Spring Boot service."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from uuid import UUID

import jwt
from fastapi import HTTPException, Request, status


ISSUER = "oep-core-lms"
AUDIENCE = "m7-ai-tutor"
STUDENT_SCOPE = "ai:student"
MAX_TTL_SECONDS = 60
CLOCK_LEEWAY_SECONDS = 3


@dataclass(frozen=True)
class StudentAssertion:
    user_id: UUID
    course_id: UUID
    request_id: UUID
    token_id: UUID


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
            return StudentAssertion(user_id, course_id, request_id, token_id)
        except (jwt.PyJWTError, ValueError, TypeError, KeyError) as exc:
            raise InvalidServiceAssertion("Invalid internal service assertion") from exc


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
