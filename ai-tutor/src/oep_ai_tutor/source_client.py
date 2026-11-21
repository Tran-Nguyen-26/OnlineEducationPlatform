"""Read an authoritative M2 source revision for an M7 indexing job."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from urllib.parse import urlparse
from uuid import UUID, uuid4

import httpx
import jwt

from oep_ai_tutor.index_jobs import IndexEvent


class SourceUnavailable(RuntimeError):
    """M2 no longer considers this content/version eligible for indexing."""


class SourceFetchError(RuntimeError):
    """The trusted M2 source read failed or violated its contract."""


@dataclass(frozen=True)
class SourceSnapshot:
    content_id: UUID
    course_id: UUID
    lesson_id: UUID
    version_number: int
    content_type: str
    source_title: str
    text_content: str | None
    file_name: str | None
    mime_type: str | None
    file_size: int | None
    download_url: str | None


def _assertion(secret: str, request_id: UUID) -> str:
    if len(secret.encode("utf-8")) < 32:
        raise ValueError("AI_TUTOR_SERVICE_JWT_SECRET must contain at least 32 bytes")
    now = int(datetime.now(timezone.utc).timestamp())
    return jwt.encode({
        "iss": "m7-ai-tutor", "aud": "oep-core-lms", "sub": "m7-ai-tutor",
        "scope": "ai:source:read", "request_id": str(request_id),
        "jti": str(uuid4()), "iat": now, "nbf": now, "exp": now + 60,
    }, secret, algorithm="HS256")


def fetch_source(event: IndexEvent, *, base_url: str, secret: str,
                 client: httpx.Client) -> SourceSnapshot:
    """M2 alone decides whether the exact event revision is currently readable."""
    if event.action != "UPSERT":
        raise ValueError("Only UPSERT jobs can fetch source content")
    if not base_url or urlparse(base_url).scheme not in ("http", "https"):
        raise ValueError("A private M2 base URL is required")
    request_id = uuid4()
    try:
        response = client.get(
            base_url.rstrip("/") + f"/internal/v1/ai/sources/{event.content_id}",
            params={"course_id": str(event.course_id),
                    "lesson_id": str(event.lesson_id),
                    "version_number": event.version_number},
            headers={"Authorization": "Bearer " + _assertion(secret, request_id),
                     "X-Request-Id": str(request_id)},
        )
    except httpx.HTTPError as exc:
        raise SourceFetchError("M2 source service is unavailable") from exc
    if response.status_code == 404:
        raise SourceUnavailable("Source version is no longer eligible")
    if response.status_code != 200:
        raise SourceFetchError("M2 source service returned an unexpected status")
    try:
        body = response.json()
        if not isinstance(body, dict) or set(body) != {
            "content_id", "course_id", "lesson_id", "version_number",
            "content_type", "source_title", "text_content", "file_name",
            "mime_type", "file_size", "download_url",
        }:
            raise ValueError("Invalid source response fields")
        if (UUID(body["content_id"]) != event.content_id
                or UUID(body["course_id"]) != event.course_id
                or UUID(body["lesson_id"]) != event.lesson_id
                or type(body["version_number"]) is not int
                or body["version_number"] != event.version_number):
            raise ValueError("Source identity differs from the job")
        kind = body["content_type"]
        title = body["source_title"]
        if kind not in ("TEXT", "PDF", "FILE") or not isinstance(title, str) or not title.strip() or len(title) > 255:
            raise ValueError("Invalid source metadata")
        if kind == "TEXT":
            if (not isinstance(body["text_content"], str) or not body["text_content"].strip()
                    or any(body[key] is not None for key in (
                        "file_name", "mime_type", "file_size", "download_url"))):
                raise ValueError("Invalid inline source")
        else:
            url = body["download_url"]
            parsed = urlparse(url) if isinstance(url, str) else None
            if (body["text_content"] is not None
                    or not isinstance(body["file_name"], str) or not body["file_name"]
                    or not isinstance(body["mime_type"], str) or not body["mime_type"]
                    or type(body["file_size"]) is not int or body["file_size"] <= 0
                    or parsed is None or parsed.scheme not in ("http", "https")
                    or not parsed.netloc or parsed.username or parsed.password):
                raise ValueError("Invalid file source")
        return SourceSnapshot(event.content_id, event.course_id, event.lesson_id,
                              event.version_number, kind, title,
                              body["text_content"], body["file_name"],
                              body["mime_type"], body["file_size"], body["download_url"])
    except (ValueError, TypeError, KeyError, AttributeError) as exc:
        raise SourceFetchError("M2 source response violated the contract") from exc
