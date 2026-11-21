"""Index jobs activate only a current, completely embedded M2 source revision."""

from __future__ import annotations

from io import BytesIO
import os
from uuid import uuid4

import httpx
import psycopg
from psycopg import sql
from psycopg.conninfo import make_conninfo
from psycopg.rows import dict_row
import pytest
from pypdf import PdfWriter
from pypdf.generic import DecodedStreamObject, DictionaryObject, NameObject

from oep_ai_tutor.index_jobs import IndexEvent, enqueue_index_event
from oep_ai_tutor.index_worker import IndexWorker, download_source
from oep_ai_tutor.migrate import migrate
from oep_ai_tutor.pgvector_store import PgvectorStore
from oep_ai_tutor.source_client import SourceFetchError, SourceSnapshot


SECRET = "worker-test-service-secret-longer-than-32-bytes"


class FakeEmbedding:
    dimension = 3

    def embed_passages(self, passages: list[str]) -> list[list[float]]:
        return [[1.0, 0.0, 0.0] for _ in passages]


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
        assert migrate(dsn) == [1, 2]
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
