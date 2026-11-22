"""Index jobs activate only a current, completely embedded M2 source revision."""

from __future__ import annotations

from io import BytesIO
from datetime import datetime, timezone
from hashlib import sha256
import os
import re
from uuid import uuid4

import httpx
import jwt
import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo
from psycopg.rows import dict_row
import pytest
from fastapi.testclient import TestClient
from pypdf import PdfWriter
from pypdf.generic import DecodedStreamObject, DictionaryObject, NameObject

from oep_ai_tutor.index_jobs import IndexEvent, enqueue_index_event
from oep_ai_tutor.embeddings import EmbeddingProviderError
from oep_ai_tutor import chat_routes
from oep_ai_tutor.app import create_app
from oep_ai_tutor.generation import GenerationResult
from oep_ai_tutor.index_worker import IndexWorker, download_source
from oep_ai_tutor.migrate import migrate
from oep_ai_tutor.pgvector_store import PgvectorStore
from oep_ai_tutor.retrieval import retrieve
from oep_ai_tutor.source_client import SourceFetchError, SourceSnapshot


SECRET = "worker-test-service-secret-longer-than-32-bytes"


class FakeEmbedding:
    dimension = 3

    def embed_passages(self, passages: list[str]) -> list[list[float]]:
        return [[1.0, 0.0, 0.0] for _ in passages]

    def embed_query(self, query: str) -> list[float]:
        return [1.0, 0.0, 0.0]


def pdf_bytes() -> bytes:
    writer = PdfWriter()
    page = writer.add_blank_page(width=612, height=792)
    font = DictionaryObject({NameObject("/Type"): NameObject("/Font"),
                             NameObject("/Subtype"): NameObject("/Type1"),
                             NameObject("/BaseFont"): NameObject("/Helvetica")})
    page[NameObject("/Resources")] = DictionaryObject({
        NameObject("/Font"): DictionaryObject({NameObject("/F1"): writer._add_object(font)})})
    stream = DecodedStreamObject()
    stream.set_data(b"BT /F1 12 Tf 72 720 Td (PDF evidence) Tj ET")
    page[NameObject("/Contents")] = writer._add_object(stream)
    output = BytesIO()
    writer.write(output)
    return output.getvalue()


@pytest.fixture(scope="module")
def worker_dsn():
    base_dsn = os.environ.get("AI_TUTOR_TEST_DATABASE_URL")
    if not base_dsn:
        pytest.skip("Set AI_TUTOR_TEST_DATABASE_URL to an isolated pgvector database")
    database_name = "oep_ai_worker_" + uuid4().hex
    with psycopg.connect(base_dsn, autocommit=True) as admin:
        admin.execute(sql.SQL("CREATE DATABASE {}").format(sql.Identifier(database_name)))
    try:
        dsn = make_conninfo(base_dsn, dbname=database_name)
        assert migrate(dsn) == [1, 2, 3]
        yield dsn
    finally:
        with psycopg.connect(base_dsn, autocommit=True) as admin:
            admin.execute(sql.SQL("DROP DATABASE {} WITH (FORCE)").format(sql.Identifier(database_name)))


def _worker(dsn: str, event: IndexEvent, *, kind: str, payload: str | bytes,
            name: str | None = None, mime: str | None = None,
            source_status: int = 200) -> IndexWorker:
    def handle(request: httpx.Request) -> httpx.Response:
        if request.url.host == "storage.test":
            return httpx.Response(200, content=payload)
        assert request.url.host == "m2.internal"
        assert request.url.path.endswith(str(event.content_id))
        if source_status != 200:
            return httpx.Response(source_status)
        return httpx.Response(200, json={
            "content_id": str(event.content_id), "course_id": str(event.course_id),
            "lesson_id": str(event.lesson_id), "version_number": event.version_number,
            "content_type": kind, "source_title": "Lesson source",
            "text_content": payload if isinstance(payload, str) else None,
            "file_name": name, "mime_type": mime,
            "file_size": len(payload) if isinstance(payload, bytes) else None,
            "download_url": "https://storage.test/file" if isinstance(payload, bytes) else None,
        })

    client = httpx.Client(transport=httpx.MockTransport(handle))
    return IndexWorker(database_url=dsn, m2_base_url="http://m2.internal",
                       service_secret=SECRET, storage_origin="https://storage.test",
                       space_key="test-space", embedding=FakeEmbedding(),
                       store=PgvectorStore(dsn), client=client)


@pytest.mark.integration
@pytest.mark.parametrize("kind,payload,name,mime,location", [
    ("TEXT", "Text evidence", None, None, (None, None, None)),
    ("PDF", pdf_bytes(), "unit.pdf", "application/pdf", (1, None, None)),
    ("FILE", b"# Heading\nMarkdown evidence", "unit.md", "text/markdown",
     (None, "Heading", None)),
    ("FILE", b'{"topic":"JSON evidence"}', "unit.json", "application/json",
     (None, None, "$/topic")),
])
def test_worker_indexes_four_formats_with_provenance(
    worker_dsn: str, kind: str, payload: str | bytes, name: str | None,
    mime: str | None, location: tuple,
) -> None:
    event = IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UPSERT")
    with psycopg.connect(worker_dsn, autocommit=True) as connection:
        enqueue_index_event(connection, event)
    worker = _worker(worker_dsn, event, kind=kind, payload=payload, name=name, mime=mime)
    try:
        assert worker.run_once()
        assert not worker.run_once()
        with psycopg.connect(worker_dsn, row_factory=dict_row) as connection:
            row = connection.execute(
                "SELECT job.status, revision.state, revision.content_id, revision.version_number, "
                "chunk.page_number, chunk.heading, chunk.json_path, chunk.embedding IS NOT NULL AS embedded "
                "FROM ai_tutor.ai_index_jobs AS job "
                "JOIN ai_tutor.ai_source_revisions AS revision ON revision.content_id = job.content_id "
                "JOIN ai_tutor.ai_source_chunks AS chunk ON chunk.source_revision_id = revision.id "
                "WHERE job.event_id = %s", (event.event_id,),
            ).fetchone()
        assert row["status"] == "SUCCEEDED" and row["state"] == "READY"
        assert row["content_id"] == event.content_id and row["version_number"] == 1
        assert (row["page_number"], row["heading"], row["json_path"]) == location
        assert row["embedded"]
        assert len(worker.store.query(course_id=event.course_id,
                                      lesson_ids=(event.lesson_id,), space_key="test-space",
                                      vector=[1.0, 0.0, 0.0], limit=5)) == 1
    finally:
        worker.client.close()


@pytest.mark.integration
def test_worker_marks_unavailable_and_unsupported_sources(worker_dsn: str) -> None:
    for status, kind, payload, expected_status, expected_state in (
        (404, "TEXT", "gone", "SUCCEEDED", None),
        (200, "PDF", b"not a PDF", "FAILED", "UNSUPPORTED"),
    ):
        event = IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UPSERT")
        with psycopg.connect(worker_dsn, autocommit=True) as connection:
            enqueue_index_event(connection, event)
        worker = _worker(worker_dsn, event, kind=kind, payload=payload,
                         name="broken.pdf" if kind == "PDF" else None,
                         mime="application/pdf" if kind == "PDF" else None,
                         source_status=status)
        try:
            assert worker.run_once()
            with psycopg.connect(worker_dsn, row_factory=dict_row) as connection:
                row = connection.execute(
                    "SELECT job.status, revision.state FROM ai_tutor.ai_index_jobs AS job "
                    "LEFT JOIN ai_tutor.ai_source_revisions AS revision ON revision.content_id = job.content_id "
                    "WHERE job.event_id = %s", (event.event_id,),
                ).fetchone()
            assert (row["status"], row["state"]) == (expected_status, expected_state)
        finally:
            worker.client.close()


@pytest.mark.integration
def test_last_retry_fails_permanently_and_releases_processing_revision(worker_dsn: str) -> None:
    event = IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UPSERT")
    with psycopg.connect(worker_dsn, autocommit=True) as connection:
        job, _ = enqueue_index_event(connection, event)
        connection.execute("UPDATE ai_tutor.ai_index_jobs SET attempt_count = 2 WHERE id = %s",
                           (job["id"],))
    worker = _worker(worker_dsn, event, kind="TEXT", payload="evidence", source_status=503)
    try:
        assert worker.run_once()
        with psycopg.connect(worker_dsn, row_factory=dict_row) as connection:
            row = connection.execute(
                "SELECT status, error_code, attempt_count FROM ai_tutor.ai_index_jobs "
                "WHERE id = %s", (job["id"],),
            ).fetchone()
        assert (row["status"], row["error_code"], row["attempt_count"]) == (
            "FAILED", "INDEX_FAILED", 3)
        assert not worker.run_once()
    finally:
        worker.client.close()


@pytest.mark.integration
def test_new_event_retries_failed_revision_after_provider_recovers(worker_dsn: str) -> None:
    first = IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UPSERT")
    with psycopg.connect(worker_dsn, autocommit=True) as connection:
        job, _ = enqueue_index_event(connection, first)
        connection.execute("UPDATE ai_tutor.ai_index_jobs SET attempt_count = 2 WHERE id = %s",
                           (job["id"],))
    worker = _worker(worker_dsn, first, kind="TEXT", payload="recovered evidence")

    class FailingEmbedding(FakeEmbedding):
        def embed_passages(self, passages: list[str]) -> list[list[float]]:
            raise EmbeddingProviderError("provider unavailable")

    worker.embedding = FailingEmbedding()
    try:
        assert worker.run_once()
    finally:
        worker.client.close()
    with psycopg.connect(worker_dsn, row_factory=dict_row) as connection:
        revision = connection.execute(
            "SELECT state FROM ai_tutor.ai_source_revisions WHERE content_id = %s",
            (first.content_id,),
        ).fetchone()
    assert revision["state"] == "FAILED"

    retry = IndexEvent(uuid4(), first.content_id, first.course_id, first.lesson_id, 1, "UPSERT")
    with psycopg.connect(worker_dsn, autocommit=True) as connection:
        enqueue_index_event(connection, retry)
    worker = _worker(worker_dsn, retry, kind="TEXT", payload="recovered evidence")
    try:
        assert worker.run_once()
        assert len(worker.store.query(course_id=first.course_id,
                                      lesson_ids=(first.lesson_id,), space_key="test-space",
                                      vector=[1.0, 0.0, 0.0], limit=5)) == 1
    finally:
        worker.client.close()
    with psycopg.connect(worker_dsn, row_factory=dict_row) as connection:
        revision = connection.execute(
            "SELECT state FROM ai_tutor.ai_source_revisions WHERE content_id = %s",
            (first.content_id,),
        ).fetchone()
    assert revision["state"] == "READY"


@pytest.mark.integration
def test_old_attempt_cannot_write_after_lease_is_replaced(worker_dsn: str) -> None:
    event = IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UPSERT")
    with psycopg.connect(worker_dsn, autocommit=True) as connection:
        job, _ = enqueue_index_event(connection, event)
    worker = _worker(worker_dsn, event, kind="TEXT", payload="evidence")

    class LeaseStealingEmbedding(FakeEmbedding):
        def embed_passages(self, passages: list[str]) -> list[list[float]]:
            with psycopg.connect(worker_dsn) as connection:
                connection.execute(
                    "UPDATE ai_tutor.ai_index_jobs SET attempt_count = attempt_count + 1 "
                    "WHERE id = %s", (job["id"],),
                )
            return super().embed_passages(passages)

    worker.embedding = LeaseStealingEmbedding()
    try:
        assert worker.run_once()
        with psycopg.connect(worker_dsn, row_factory=dict_row) as connection:
            row = connection.execute(
                "SELECT revision.state, chunk.embedding IS NOT NULL AS embedded "
                "FROM ai_tutor.ai_source_revisions AS revision "
                "JOIN ai_tutor.ai_source_chunks AS chunk ON chunk.source_revision_id = revision.id "
                "WHERE revision.content_id = %s", (event.content_id,),
            ).fetchone()
        assert row == {"state": "PROCESSING", "embedded": False}
    finally:
        worker.client.close()


@pytest.mark.integration
def test_later_unavailable_snapshot_hides_previously_ready_evidence(worker_dsn: str) -> None:
    first = IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UPSERT")
    with psycopg.connect(worker_dsn, autocommit=True) as connection:
        enqueue_index_event(connection, first)
    worker = _worker(worker_dsn, first, kind="TEXT", payload="old evidence")
    try:
        assert worker.run_once()
    finally:
        worker.client.close()
    repeated = IndexEvent(uuid4(), first.content_id, first.course_id,
                          first.lesson_id, first.version_number, "UPSERT")
    with psycopg.connect(worker_dsn, autocommit=True) as connection:
        enqueue_index_event(connection, repeated)
    worker = _worker(worker_dsn, repeated, kind="TEXT", payload="old evidence",
                     source_status=404)
    try:
        assert worker.run_once()
        assert worker.store.query(course_id=first.course_id, lesson_ids=(first.lesson_id,),
                                  space_key="test-space", vector=[1.0, 0.0, 0.0],
                                  limit=5) == []
    finally:
        worker.client.close()


@pytest.mark.integration
def test_retrieval_revalidates_course_lesson_and_newer_job(worker_dsn: str) -> None:
    event = IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UPSERT")
    with psycopg.connect(worker_dsn, autocommit=True) as connection:
        enqueue_index_event(connection, event)
    worker = _worker(worker_dsn, event, kind="TEXT", payload="private course evidence")
    try:
        assert worker.run_once()
        def search(course_id, lesson_ids):
            with psycopg.connect(worker_dsn, row_factory=dict_row) as connection:
                return retrieve(connection, store=worker.store, embedding=FakeEmbedding(),
                                course_id=course_id, lesson_ids=lesson_ids,
                                space_key="test-space", query="evidence")
        assert len(search(event.course_id, (event.lesson_id,))) == 1
        assert search(uuid4(), (event.lesson_id,)) == []
        assert search(event.course_id, (uuid4(),)) == []
        newer = IndexEvent(uuid4(), event.content_id, event.course_id,
                           event.lesson_id, 2, "UPSERT")
        with psycopg.connect(worker_dsn, autocommit=True) as connection:
            enqueue_index_event(connection, newer)
        assert search(event.course_id, (event.lesson_id,)) == []
        with psycopg.connect(worker_dsn, autocommit=True) as connection:
            connection.execute(
                "UPDATE ai_tutor.ai_index_jobs SET status = 'SUCCEEDED' WHERE event_id = %s",
                (newer.event_id,),
            )
    finally:
        worker.client.close()


@pytest.mark.integration
def test_worker_waits_for_course_republication(worker_dsn: str) -> None:
    event = IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UPSERT")
    with psycopg.connect(worker_dsn, autocommit=True) as connection:
        enqueue_index_event(connection, event)
        connection.execute(
            "INSERT INTO ai_tutor.ai_course_access (course_id, course_version, active) "
            "VALUES (%s, 2, false)", (event.course_id,),
        )
    worker = _worker(worker_dsn, event, kind="TEXT", payload="republished evidence")
    try:
        assert not worker.run_once()
        with psycopg.connect(worker_dsn, autocommit=True) as connection:
            connection.execute(
                "UPDATE ai_tutor.ai_course_access SET active = true, course_version = 3 "
                "WHERE course_id = %s", (event.course_id,),
            )
        assert worker.run_once()
        assert len(worker.store.query(course_id=event.course_id,
                                      lesson_ids=(event.lesson_id,), space_key="test-space",
                                      vector=[1.0, 0.0, 0.0], limit=5)) == 1
    finally:
        worker.client.close()


@pytest.mark.integration
def test_inflight_course_deactivation_requeues_before_activation(worker_dsn: str) -> None:
    event = IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UPSERT")
    with psycopg.connect(worker_dsn, autocommit=True) as connection:
        enqueue_index_event(connection, event)
    worker = _worker(worker_dsn, event, kind="TEXT", payload="evidence")

    class ArchivingEmbedding(FakeEmbedding):
        def embed_passages(self, passages: list[str]) -> list[list[float]]:
            with psycopg.connect(worker_dsn) as connection:
                connection.execute(
                    "INSERT INTO ai_tutor.ai_course_access "
                    "(course_id, course_version, active) VALUES (%s, 2, false)",
                    (event.course_id,),
                )
            return super().embed_passages(passages)

    worker.embedding = ArchivingEmbedding()
    try:
        assert worker.run_once()
        with psycopg.connect(worker_dsn, row_factory=dict_row) as connection:
            job = connection.execute("SELECT status FROM ai_tutor.ai_index_jobs "
                                     "WHERE event_id = %s", (event.event_id,)).fetchone()
        assert job["status"] == "QUEUED"
        assert worker.store.query(course_id=event.course_id, lesson_ids=(event.lesson_id,),
                                  space_key="test-space", vector=[1.0, 0.0, 0.0], limit=5) == []
        with psycopg.connect(worker_dsn) as connection:
            connection.execute("UPDATE ai_tutor.ai_course_access SET active = true, "
                               "course_version = 3 WHERE course_id = %s", (event.course_id,))
        worker.embedding = FakeEmbedding()
        assert worker.run_once()
        assert len(worker.store.query(course_id=event.course_id,
                                      lesson_ids=(event.lesson_id,), space_key="test-space",
                                      vector=[1.0, 0.0, 0.0], limit=5)) == 1
    finally:
        worker.client.close()


@pytest.mark.integration
def test_student_can_answer_from_each_indexed_format_with_citation(
    worker_dsn: str, monkeypatch: pytest.MonkeyPatch,
) -> None:
    course, user, conversation = uuid4(), uuid4(), uuid4()
    sources = [
        ("TEXT", "Text evidence", None, None, "Text evidence", None, None, None),
        ("PDF", pdf_bytes(), "unit.pdf", "application/pdf", "PDF evidence", 1, None, None),
        ("FILE", b"# Heading\nMarkdown evidence", "unit.md", "text/markdown",
         "Markdown evidence", None, "Heading", None),
        ("FILE", b'{"topic":"JSON evidence"}', "unit.json", "application/json",
         "JSON evidence", None, None, "$/topic"),
    ]
    events = []
    for kind, payload, name, mime, *_ in sources:
        event = IndexEvent(uuid4(), uuid4(), course, uuid4(), 1, "UPSERT")
        with psycopg.connect(worker_dsn, autocommit=True) as connection:
            enqueue_index_event(connection, event)
        worker = _worker(worker_dsn, event, kind=kind, payload=payload,
                         name=name, mime=mime)
        try:
            assert worker.run_once()
        finally:
            worker.client.close()
        events.append(event)
    with psycopg.connect(worker_dsn) as connection:
        connection.execute(
            "INSERT INTO ai_tutor.ai_conversations "
            "(id, user_id, course_id, title) VALUES (%s, %s, %s, 'Four formats')",
            (conversation, user, course),
        )

    class CitingGeneration:
        model = "fake"
        keyword = ""

        def generate(self, instructions, messages):
            prompt = messages[-1].content
            assert "untrusted" in instructions.lower()
            for match in re.finditer(
                r"\[(\d+)\][^\n]*\n(.*?)(?=\n\n\[\d+\]|\Z)", prompt, re.DOTALL,
            ):
                if self.keyword in match.group(2):
                    return GenerationResult(f"{self.keyword} [{match.group(1)}]", 1, 1, self.model)
            raise AssertionError(f"Expected {self.keyword!r} in prompt: {prompt!r}")

    model = CitingGeneration()
    monkeypatch.setenv("AI_TUTOR_DATABASE_URL", worker_dsn)
    monkeypatch.setenv("AI_TUTOR_SERVICE_JWT_SECRET", SECRET)
    monkeypatch.setenv("AI_TUTOR_EMBEDDING_SPACE_KEY", "test-space")
    monkeypatch.setenv("AI_TUTOR_M2_BASE_URL", "http://m2.internal")
    monkeypatch.setattr(chat_routes, "embedding_provider_from_env", lambda _: FakeEmbedding())
    monkeypatch.setattr(chat_routes, "generation_provider_from_env", lambda _: model)
    monkeypatch.setattr(chat_routes, "vector_store_from_env", lambda **_: PgvectorStore(worker_dsn))
    monkeypatch.setattr(chat_routes, "fetch_source", lambda event, **_: SourceSnapshot(
        event.content_id, event.course_id, event.lesson_id, event.version_number,
        "TEXT", "Lesson source", "eligible", None, None, None, None))
    lesson_ids = tuple(event.lesson_id for event in events)
    digest = sha256("\n".join(sorted(str(value) for value in lesson_ids)).encode()).hexdigest()
    request_id = uuid4()
    now = int(datetime.now(timezone.utc).timestamp())
    token = jwt.encode({
        "iss": "oep-core-lms", "aud": "m7-ai-tutor", "scope": "ai:student",
        "sub": str(user), "course_id": str(course), "request_id": str(request_id),
        "jti": str(uuid4()), "iat": now, "nbf": now, "exp": now + 45,
        "lesson_scope_sha256": digest,
    }, SECRET, algorithm="HS256")
    path = f"/internal/v1/ai/courses/{course}/conversations/{conversation}/messages"
    with TestClient(create_app()) as client:
        for event, (_, _, _, _, keyword, page, heading, json_path) in zip(events, sources):
            model.keyword = keyword
            response = client.post(
                path,
                headers={"Authorization": "Bearer " + token,
                         "X-Request-Id": str(request_id),
                         "Idempotency-Key": str(uuid4())},
                json={"message": f"Explain {keyword}",
                      "readable_lesson_ids": [str(value) for value in lesson_ids]},
            )
            assert response.status_code == 201, response.text
            answer = response.json()["assistant_message"]
            assert answer["answer_status"] == "ANSWERED"
            assert len(answer["citations"]) == 1
            citation = answer["citations"][0]
            assert citation["content_id"] == str(event.content_id)
            assert (citation["page_number"], citation["heading"],
                    citation["json_path"]) == (page, heading, json_path)


def test_download_rejects_wrong_origin_redirect_and_size() -> None:
    event = IndexEvent(uuid4(), uuid4(), uuid4(), uuid4(), 1, "UPSERT")
    source = SourceSnapshot(event.content_id, event.course_id, event.lesson_id, 1,
                            "FILE", "Source", None, "unit.md", "text/markdown", 3,
                            "https://storage.test/file")
    with httpx.Client(transport=httpx.MockTransport(lambda _: httpx.Response(200, content=b"abcd"))) as client:
        with pytest.raises(SourceFetchError):
            download_source(source, client=client, storage_origin="https://storage.test")
        with pytest.raises(SourceFetchError):
            download_source(source, client=client, storage_origin="https://elsewhere.test")
    with httpx.Client(transport=httpx.MockTransport(lambda _: httpx.Response(
            302, headers={"location": "https://elsewhere.test/file"}))) as client:
        with pytest.raises(SourceFetchError):
            download_source(source, client=client, storage_origin="https://storage.test")
