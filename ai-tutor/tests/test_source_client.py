"""M7 accepts only a current, scope-matching M2 source snapshot."""

from __future__ import annotations

from uuid import uuid4

import httpx
import jwt
import pytest

from oep_ai_tutor.index_jobs import IndexEvent
from oep_ai_tutor.source_client import SourceFetchError, SourceUnavailable, fetch_source


SECRET = "source-read-contract-secret-longer-than-32-bytes"


def _event() -> IndexEvent:
    return IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 3, "UPSERT")


def _body(event: IndexEvent) -> dict:
    return {"content_id": str(event.content_id), "course_id": str(event.course_id),
            "lesson_id": str(event.lesson_id), "version_number": event.version_number,
            "content_type": "TEXT", "source_title": "Lesson", "text_content": "Safe text",
            "file_name": None, "mime_type": None, "file_size": None, "download_url": None}


def test_fetch_binds_signed_request_to_current_text_revision() -> None:
    event = _event()

    def handle(request: httpx.Request) -> httpx.Response:
        claims = jwt.decode(request.headers["authorization"].removeprefix("Bearer "),
                            SECRET, algorithms=["HS256"], audience="oep-core-lms",
                            issuer="m7-ai-tutor")
        assert claims["sub"] == "m7-ai-tutor"
        assert claims["scope"] == "ai:source:read"
        assert claims["request_id"] == request.headers["x-request-id"]
        assert request.url.path.endswith(str(event.content_id))
        assert request.url.params["course_id"] == str(event.course_id)
        assert request.url.params["lesson_id"] == str(event.lesson_id)
        assert request.url.params["version_number"] == "3"
        return httpx.Response(200, json=_body(event))

    with httpx.Client(transport=httpx.MockTransport(handle)) as client:
        source = fetch_source(event, base_url="http://m2.internal", secret=SECRET, client=client)
    assert source.text_content == "Safe text"
    assert source.download_url is None


def test_file_source_requires_exact_identity_and_private_read_grant() -> None:
    event = _event()
    body = {**_body(event), "content_type": "PDF", "text_content": None,
            "file_name": "lecture.pdf", "mime_type": "application/pdf",
            "file_size": 123, "download_url": "https://storage.test/signed"}
    with httpx.Client(transport=httpx.MockTransport(lambda _: httpx.Response(200, json=body))) as client:
        source = fetch_source(event, base_url="http://m2.internal", secret=SECRET, client=client)
    assert source.file_size == 123 and source.download_url == body["download_url"]

    for invalid in ({**body, "course_id": str(uuid4())},
                    {**body, "version_number": 2},
                    {**body, "download_url": "file:///private/object"},
                    {**body, "download_url": "http://user:password@storage.test/object"}):
        with httpx.Client(transport=httpx.MockTransport(
                lambda _, value=invalid: httpx.Response(200, json=value))) as client:
            with pytest.raises(SourceFetchError):
                fetch_source(event, base_url="http://m2.internal", secret=SECRET, client=client)


def test_not_found_is_nonretryable_but_service_errors_are_not() -> None:
    event = _event()
    with httpx.Client(transport=httpx.MockTransport(lambda _: httpx.Response(404))) as client:
        with pytest.raises(SourceUnavailable):
            fetch_source(event, base_url="http://m2.internal", secret=SECRET, client=client)
    with httpx.Client(transport=httpx.MockTransport(lambda _: httpx.Response(503))) as client:
        with pytest.raises(SourceFetchError):
            fetch_source(event, base_url="http://m2.internal", secret=SECRET, client=client)


def test_rejects_invalid_configuration_and_non_upsert_job() -> None:
    event = _event()
    with httpx.Client(transport=httpx.MockTransport(lambda _: httpx.Response(200))) as client:
        with pytest.raises(ValueError):
            fetch_source(event, base_url="", secret=SECRET, client=client)
        with pytest.raises(ValueError):
            fetch_source(IndexEvent(event.event_id, event.content_id, event.course_id,
                                    event.lesson_id, 3, "DEACTIVATE"),
                         base_url="http://m2.internal", secret=SECRET, client=client)
