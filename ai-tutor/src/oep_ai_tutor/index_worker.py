"""Process durable M2 source jobs into versioned, queryable M7 chunks."""

from __future__ import annotations

from hashlib import sha256
import logging
import os
import time
from urllib.parse import urlsplit
from uuid import UUID, uuid4, uuid5

import httpx
import psycopg
from psycopg.rows import dict_row

from oep_ai_tutor.embeddings import EmbeddingProvider, EmbeddingProviderError, embedding_provider_from_env
from oep_ai_tutor.index_jobs import IndexEvent, superseded_event
from oep_ai_tutor.parsing import UnsupportedSource, chunk_passages, parse_source
from oep_ai_tutor.source_client import SourceFetchError, SourceSnapshot, SourceUnavailable, fetch_source
from oep_ai_tutor.vector_store import VectorRecord, VectorStore, vector_store_from_env


LOG = logging.getLogger(__name__)
CHUNK_NAMESPACE = UUID("d535704f-b2be-486f-b89b-e0ae9d464a4b")
MAX_ATTEMPTS = 3


class StaleAttempt(RuntimeError):
    """A newer worker lease or source revocation superseded this attempt."""


def _event(row: dict) -> IndexEvent:
    return IndexEvent(row["event_id"], row["content_id"], row["course_id"],
                      row["lesson_id"], row["version_number"], row["action"])


def claim_next_job(connection: psycopg.Connection) -> dict | None:
    """Lease one job; a crashed PROCESSING lease may be reclaimed after 15 minutes."""
    with connection.transaction():
        row = connection.execute(
            "SELECT id, event_id, content_id, course_id, lesson_id, version_number, "
            "action, attempt_count FROM ai_tutor.ai_index_jobs "
            "WHERE action = 'UPSERT' AND (status = 'QUEUED' "
            "OR (status = 'FAILED' AND error_code = 'RETRYABLE' "
            "AND attempt_count < %s AND updated_at < now() - interval '30 seconds') "
            "OR (status = 'PROCESSING' AND updated_at < now() - interval '15 minutes')) "
            "AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_course_access AS access "
            "WHERE access.course_id = ai_index_jobs.course_id AND access.active = false) "
            "ORDER BY created_at, id FOR UPDATE SKIP LOCKED LIMIT 1",
            (MAX_ATTEMPTS,),
        ).fetchone()
        if row is None:
            return None
        attempt = row["attempt_count"] + 1
        connection.execute(
            "UPDATE ai_tutor.ai_index_jobs SET status = 'PROCESSING', "
            "attempt_count = %s, error_code = NULL, updated_at = now() WHERE id = %s",
            (attempt, row["id"]),
        )
        return {**row, "attempt_count": attempt}


def download_source(source: SourceSnapshot, *, client: httpx.Client,
                    storage_origin: str) -> str | bytes:
    if source.content_type == "TEXT":
        assert source.text_content is not None
        return source.text_content
    parsed = urlsplit(source.download_url or "")
    origin = f"{parsed.scheme}://{parsed.netloc}"
    if not storage_origin or origin != storage_origin.rstrip("/"):
        raise SourceFetchError("Storage grant origin is not configured or does not match")
    maximum = 20 * 1024 * 1024 if source.content_type == "PDF" else 5 * 1024 * 1024
    if source.file_size is None or source.file_size > maximum:
        raise SourceFetchError("Source file exceeds its format limit")
    parts: list[bytes] = []
    total = 0
    try:
        with client.stream("GET", source.download_url, follow_redirects=False) as response:
            response.raise_for_status()
            for part in response.iter_bytes():
                total += len(part)
                if total > maximum or total > source.file_size:
                    raise SourceFetchError("Downloaded source exceeds its declared size")
                parts.append(part)
    except httpx.HTTPError as exc:
        raise SourceFetchError("Private source download failed") from exc
    if total != source.file_size:
        raise SourceFetchError("Downloaded source size differs from M2 metadata")
    return b"".join(parts)


class IndexWorker:
    def __init__(self, *, database_url: str, m2_base_url: str, service_secret: str,
                 storage_origin: str, space_key: str, embedding: EmbeddingProvider,
                 store: VectorStore, client: httpx.Client):
        if not database_url or not m2_base_url or not service_secret or not space_key:
            raise ValueError("Index worker database, M2, secret and embedding space are required")
        self.database_url = database_url
        self.m2_base_url = m2_base_url
        self.service_secret = service_secret
        self.storage_origin = storage_origin
        self.space_key = space_key
        self.embedding = embedding
        self.store = store
        self.client = client

    def _connect(self) -> psycopg.Connection:
        return psycopg.connect(self.database_url, autocommit=True, row_factory=dict_row)

    def run_once(self) -> bool:
        with self._connect() as connection:
            job = claim_next_job(connection)
            if job is None:
                return False
            try:
                self._process(connection, job)
            except UnsupportedSource as exc:
                self._finish(connection, job, state="UNSUPPORTED", error_code=exc.code)
            except SourceUnavailable:
                self._finish(connection, job, state="INACTIVE")
            except StaleAttempt:
                pass
            except (SourceFetchError, EmbeddingProviderError, httpx.HTTPError,
                    psycopg.Error, ValueError) as exc:
                LOG.warning("AI index job %s will retry: %s", job["id"], type(exc).__name__)
                final = job["attempt_count"] >= MAX_ATTEMPTS
                self._finish(connection, job, state="FAILED" if final else None,
                             error_code="INDEX_FAILED" if final else "RETRYABLE")
            return True

    def _process(self, connection: psycopg.Connection, job: dict) -> None:
        event = _event(job)
        if superseded_event(connection, event):
            self._finish(connection, job, state="SUPERSEDED")
            return
        source = fetch_source(event, base_url=self.m2_base_url,
                              secret=self.service_secret, client=self.client)
        revision_id = self._prepare_revision(connection, job, source)
        if revision_id is None:
            self._finish(connection, job, state="READY")
            return
        payload = download_source(source, client=self.client,
                                  storage_origin=self.storage_origin)
        passages = parse_source(source.content_type, payload,
                                file_name=source.file_name, mime_type=source.mime_type)
        chunks = chunk_passages(passages)
        if not chunks:
            raise UnsupportedSource("EMPTY_SOURCE")
        digest = sha256(payload.encode("utf-8") if isinstance(payload, str) else payload).hexdigest()
        with connection.transaction():
            self._ensure_current_attempt(connection, job)
            revision = connection.execute(
                "SELECT state FROM ai_tutor.ai_source_revisions WHERE id = %s FOR UPDATE",
                (revision_id,),
            ).fetchone()
            if revision is None or revision["state"] != "PROCESSING":
                raise SourceUnavailable("Revision was deactivated during parsing")
            connection.execute("DELETE FROM ai_tutor.ai_source_chunks WHERE source_revision_id = %s",
                               (revision_id,))
            connection.execute("UPDATE ai_tutor.ai_source_revisions SET checksum = %s, "
                               "updated_at = now() WHERE id = %s AND state = 'PROCESSING'",
                               (digest, revision_id))
            for chunk in chunks:
                chunk_id = uuid5(CHUNK_NAMESPACE, f"{revision_id}:{chunk.index}")
                connection.execute(
                    "INSERT INTO ai_tutor.ai_source_chunks "
                    "(id, source_revision_id, course_id, lesson_id, chunk_index, chunk_text, "
                    "token_count, page_number, heading, json_path, embedding_space_key, "
                    "embedding_dimension) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)",
                    (chunk_id, revision_id, event.course_id, event.lesson_id, chunk.index,
                     chunk.text, max(1, len(chunk.text.split())), chunk.page_number,
                     chunk.heading, chunk.json_path, self.space_key, self.embedding.dimension),
                )
        for start in range(0, len(chunks), 32):
            batch = chunks[start:start + 32]
            vectors = self.embedding.embed_passages([chunk.text for chunk in batch])
            if len(vectors) != len(batch):
                raise EmbeddingProviderError("Embedding batch count mismatch")
            for chunk, values in zip(batch, vectors):
                with connection.transaction():
                    self._ensure_current_attempt(connection, job)
                    self.store.upsert(VectorRecord(
                        uuid5(CHUNK_NAMESPACE, f"{revision_id}:{chunk.index}"), revision_id,
                        event.course_id, event.lesson_id, self.space_key, values))
        # The source may have been hidden while embedding was in flight.
        fetch_source(event, base_url=self.m2_base_url,
                     secret=self.service_secret, client=self.client)
        self._activate(connection, job, revision_id)

    def _prepare_revision(self, connection: psycopg.Connection,
                          job: dict, source: SourceSnapshot) -> UUID | None:
        event = _event(job)
        with connection.transaction():
            self._ensure_current_attempt(connection, job)
            row = connection.execute(
                "SELECT id, state, course_id, lesson_id, content_type FROM "
                "ai_tutor.ai_source_revisions WHERE content_id = %s "
                "AND version_number = %s AND embedding_space_key = %s FOR UPDATE",
                (event.content_id, event.version_number, self.space_key),
            ).fetchone()
            if row is not None:
                if (row["course_id"] != event.course_id or row["lesson_id"] != event.lesson_id
                        or row["content_type"] != source.content_type):
                    raise ValueError("Existing revision has a different source scope")
                if row["state"] == "READY":
                    return None
                if row["state"] == "SUPERSEDED":
                    raise SourceUnavailable("Revision was deactivated")
                revision_id = row["id"]
                connection.execute(
                    "UPDATE ai_tutor.ai_source_revisions SET state = 'PROCESSING', "
                    "source_title = %s, deactivated_at = NULL, updated_at = now() WHERE id = %s",
                    (source.source_title, revision_id),
                )
                return revision_id
            revision_id = uuid4()
            connection.execute(
                "INSERT INTO ai_tutor.ai_source_revisions "
                "(id, content_id, version_number, course_id, lesson_id, content_type, "
                "source_title, state, embedding_space_key) "
                "VALUES (%s, %s, %s, %s, %s, %s, %s, 'PROCESSING', %s)",
                (revision_id, event.content_id, event.version_number,
                 event.course_id, event.lesson_id, source.content_type,
                 source.source_title, self.space_key),
            )
            return revision_id

    @staticmethod
    def _ensure_current_attempt(connection: psycopg.Connection, job: dict) -> None:
        current = connection.execute(
            "SELECT status, attempt_count FROM ai_tutor.ai_index_jobs WHERE id = %s FOR UPDATE",
            (job["id"],),
        ).fetchone()
        if (current is None or current["status"] != "PROCESSING"
                or current["attempt_count"] != job["attempt_count"]):
            raise StaleAttempt("Index job lease changed")

    def _activate(self, connection: psycopg.Connection, job: dict, revision_id: UUID) -> None:
        event = _event(job)
        with connection.transaction():
            current = connection.execute(
                "SELECT status, attempt_count FROM ai_tutor.ai_index_jobs WHERE id = %s FOR UPDATE",
                (job["id"],),
            ).fetchone()
            revision = connection.execute(
                "SELECT state FROM ai_tutor.ai_source_revisions WHERE id = %s FOR UPDATE",
                (revision_id,),
            ).fetchone()
            if current is None or current["status"] != "PROCESSING" or current["attempt_count"] != job["attempt_count"]:
                return
            inactive = connection.execute(
                "SELECT 1 FROM ai_tutor.ai_course_access WHERE course_id = %s AND active = false",
                (event.course_id,),
            ).fetchone()
            if inactive is not None:
                self._set_job(connection, job, "QUEUED", None)
                return
            if revision is None or revision["state"] != "PROCESSING" or superseded_event(connection, event):
                if revision is not None and revision["state"] == "PROCESSING":
                    connection.execute("UPDATE ai_tutor.ai_source_revisions SET state = 'SUPERSEDED', "
                                       "updated_at = now() WHERE id = %s", (revision_id,))
                self._set_job(connection, job, "SUCCEEDED", None)
                return
            connection.execute(
                "UPDATE ai_tutor.ai_source_revisions SET state = 'SUPERSEDED', updated_at = now() "
                "WHERE content_id = %s AND embedding_space_key = %s AND state = 'READY' AND id <> %s",
                (event.content_id, self.space_key, revision_id),
            )
            connection.execute(
                "UPDATE ai_tutor.ai_source_revisions SET state = 'READY', "
                "activated_at = now(), updated_at = now() WHERE id = %s", (revision_id,),
            )
            self._set_job(connection, job, "SUCCEEDED", None)

    def _finish(self, connection: psycopg.Connection, job: dict, *,
                state: str | None, error_code: str | None = None) -> None:
        with connection.transaction():
            current = connection.execute(
                "SELECT status, attempt_count FROM ai_tutor.ai_index_jobs WHERE id = %s FOR UPDATE",
                (job["id"],),
            ).fetchone()
            if current is None or current["status"] != "PROCESSING" or current["attempt_count"] != job["attempt_count"]:
                return
            if state is not None:
                mutable_states = ("PROCESSING", "READY") if state in (
                    "INACTIVE", "SUPERSEDED") else ("PROCESSING",)
                connection.execute(
                    "UPDATE ai_tutor.ai_source_revisions SET state = %s, updated_at = now(), "
                    "deactivated_at = CASE WHEN %s = 'INACTIVE' THEN now() ELSE deactivated_at END "
                    "WHERE content_id = %s AND version_number = %s AND embedding_space_key = %s "
                    "AND state = ANY(%s)",
                    (state, state, job["content_id"], job["version_number"],
                     self.space_key, list(mutable_states)),
                )
            self._set_job(connection, job, "FAILED" if error_code else "SUCCEEDED", error_code)

    @staticmethod
    def _set_job(connection: psycopg.Connection, job: dict, status: str,
                 error_code: str | None) -> None:
        connection.execute(
            "UPDATE ai_tutor.ai_index_jobs SET status = %s, error_code = %s, "
            "updated_at = now() WHERE id = %s",
            (status, error_code, job["id"]),
        )


def main() -> None:
    logging.basicConfig(level=logging.INFO)
    with httpx.Client(timeout=30.0) as client:
        worker = IndexWorker(
            database_url=os.environ.get("AI_TUTOR_DATABASE_URL", ""),
            m2_base_url=os.environ.get("AI_TUTOR_M2_BASE_URL", ""),
            service_secret=os.environ.get("AI_TUTOR_SERVICE_JWT_SECRET", ""),
            storage_origin=os.environ.get("AI_TUTOR_STORAGE_ORIGIN", ""),
            space_key=os.environ.get("AI_TUTOR_EMBEDDING_SPACE_KEY", ""),
            embedding=embedding_provider_from_env(client),
            store=vector_store_from_env(client=client), client=client,
        )
        while True:
            try:
                if not worker.run_once():
                    time.sleep(1)
            except KeyboardInterrupt:
                return
            except psycopg.Error as exc:
                LOG.warning("AI index database unavailable: %s", type(exc).__name__)
                time.sleep(5)


if __name__ == "__main__":
    main()
