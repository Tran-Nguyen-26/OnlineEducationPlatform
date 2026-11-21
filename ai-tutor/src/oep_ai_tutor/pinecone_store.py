"""Pinecone vector transport with PostgreSQL as the source of truth."""

from __future__ import annotations

import math
from urllib.parse import urlsplit
from uuid import UUID

import httpx
import psycopg
from psycopg.rows import dict_row

from oep_ai_tutor.pgvector_store import _checked_vector
from oep_ai_tutor.vector_store import VectorMatch, VectorRecord


class PineconeStore:
    """Use a dense, cosine, bring-your-own-vectors index for one embedding space."""

    def __init__(self, dsn: str, *, host: str, api_key: str, namespace: str,
                 dimension: int, client: httpx.Client):
        if not dsn or not host or not api_key or not namespace or dimension <= 0:
            raise ValueError("Pinecone database, host, key, namespace and dimension are required")
        parsed = urlsplit(host)
        if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.path not in ("", "/") or parsed.query or parsed.fragment:
            raise ValueError("Pinecone host must be an HTTPS index origin")
        self.dsn = dsn
        self.host = host.rstrip("/")
        self.namespace = namespace
        self.dimension = dimension
        self.client = client
        self.headers = {"Api-Key": api_key, "X-Pinecone-Api-Version": "2025-04"}

    def _post(self, path: str, body: dict) -> dict:
        response = self.client.post(self.host + path, json=body, headers=self.headers)
        response.raise_for_status()
        payload = response.json()
        if not isinstance(payload, dict):
            raise ValueError("Malformed Pinecone response")
        return payload

    def upsert(self, record: VectorRecord) -> None:
        if not record.space_key or len(record.values) != self.dimension:
            raise ValueError("Embedding space/dimension mismatch")
        values = _checked_vector(record.values).to_list()
        # Hold the row lock across the remote write so deactivation cannot race it.
        with psycopg.connect(self.dsn, row_factory=dict_row) as connection:
            row = connection.execute(
                "SELECT chunk.id FROM ai_tutor.ai_source_chunks AS chunk "
                "JOIN ai_tutor.ai_source_revisions AS revision "
                "ON revision.id = chunk.source_revision_id "
                "WHERE chunk.id = %s AND chunk.source_revision_id = %s "
                "AND chunk.course_id = %s AND chunk.lesson_id = %s "
                "AND chunk.embedding_space_key = %s AND chunk.embedding_dimension = %s "
                "AND revision.state IN ('PENDING', 'PROCESSING', 'READY') "
                "FOR UPDATE OF revision, chunk",
                (record.chunk_id, record.revision_id, record.course_id,
                 record.lesson_id, record.space_key, self.dimension),
            ).fetchone()
            if row is None:
                raise ValueError("Chunk is missing, inactive or has different scope/dimension")
            result = self._post("/vectors/upsert", {
                "namespace": self.namespace,
                "vectors": [{"id": str(record.chunk_id), "values": values,
                             "metadata": {"revision_id": str(record.revision_id),
                                          "course_id": str(record.course_id),
                                          "lesson_id": str(record.lesson_id),
                                          "space_key": record.space_key,
                                          "dimension": self.dimension}}],
            })
            if result.get("upsertedCount") != 1:
                raise ValueError("Pinecone did not acknowledge the vector")

    def query(self, *, course_id: UUID, lesson_ids: tuple[UUID, ...], space_key: str,
              vector: list[float], limit: int) -> list[VectorMatch]:
        if not space_key or not 1 <= limit <= 100 or len(vector) != self.dimension:
            raise ValueError("Embedding space, dimension and bounded positive limit are required")
        values = _checked_vector(vector).to_list()
        if not lesson_ids:
            return []
        with psycopg.connect(self.dsn, row_factory=dict_row) as connection:
            rows = connection.execute(
                "SELECT revision.id FROM ai_tutor.ai_source_revisions AS revision "
                "WHERE revision.course_id = %s AND revision.lesson_id = ANY(%s) "
                "AND revision.embedding_space_key = %s AND revision.state = 'READY' "
                "AND revision.deactivated_at IS NULL "
                "AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_course_access AS access "
                "WHERE access.course_id = revision.course_id AND access.active = false) "
                "AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_source_revisions AS newer "
                "WHERE newer.content_id = revision.content_id "
                "AND newer.embedding_space_key = revision.embedding_space_key "
                "AND newer.version_number > revision.version_number) "
                "AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_index_jobs AS newer_job "
                "WHERE newer_job.content_id = revision.content_id "
                "AND (newer_job.version_number > revision.version_number "
                "OR (newer_job.version_number = revision.version_number "
                "AND newer_job.action = 'DEACTIVATE')))",
                (course_id, list(lesson_ids), space_key),
            ).fetchall()
        if not rows:
            return []
        candidates: dict[UUID, float] = {}
        # Bound each metadata filter; merge results across batches of current revisions.
        revision_ids = [str(row["id"]) for row in rows]
        for start in range(0, len(revision_ids), 100):
            payload = self._post("/query", {
                "namespace": self.namespace, "vector": values, "topK": limit,
                "includeValues": False, "includeMetadata": False,
                "filter": {"$and": [
                    {"course_id": {"$eq": str(course_id)}},
                    {"lesson_id": {"$in": [str(value) for value in lesson_ids]}},
                    {"space_key": {"$eq": space_key}},
                    {"dimension": {"$eq": self.dimension}},
                    {"revision_id": {"$in": revision_ids[start:start + 100]}},
                ]},
            })
            matches = payload.get("matches")
            if not isinstance(matches, list):
                raise ValueError("Malformed Pinecone matches")
            for match in matches:
                if not isinstance(match, dict):
                    raise ValueError("Malformed Pinecone match")
                try:
                    chunk_id = UUID(match["id"])
                    score = float(match["score"])
                except (KeyError, TypeError, ValueError) as exc:
                    raise ValueError("Malformed Pinecone match") from exc
                if not math.isfinite(score) or not -1.000001 <= score <= 1.000001:
                    raise ValueError("Invalid Pinecone cosine score")
                candidates[chunk_id] = max(candidates.get(chunk_id, -math.inf), score)
        if not candidates:
            return []
        # Pinecone metadata is an acceleration hint, never an authorization decision.
        with psycopg.connect(self.dsn, row_factory=dict_row) as connection:
            valid = connection.execute(
                "SELECT chunk.id FROM ai_tutor.ai_source_chunks AS chunk "
                "JOIN ai_tutor.ai_source_revisions AS revision "
                "ON revision.id = chunk.source_revision_id "
                "WHERE chunk.id = ANY(%s) AND chunk.course_id = %s "
                "AND chunk.lesson_id = ANY(%s) AND chunk.embedding_space_key = %s "
                "AND chunk.embedding_dimension = %s AND revision.state = 'READY' "
                "AND revision.deactivated_at IS NULL "
                "AND revision.course_id = %s AND revision.lesson_id = chunk.lesson_id "
                "AND revision.embedding_space_key = %s "
                "AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_course_access AS access "
                "WHERE access.course_id = revision.course_id AND access.active = false) "
                "AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_source_revisions AS newer "
                "WHERE newer.content_id = revision.content_id "
                "AND newer.embedding_space_key = revision.embedding_space_key "
                "AND newer.version_number > revision.version_number) "
                "AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_index_jobs AS newer_job "
                "WHERE newer_job.content_id = revision.content_id "
                "AND (newer_job.version_number > revision.version_number "
                "OR (newer_job.version_number = revision.version_number "
                "AND newer_job.action = 'DEACTIVATE')))",
                (list(candidates), course_id, list(lesson_ids), space_key,
                 self.dimension, course_id, space_key),
            ).fetchall()
        hits = [VectorMatch(row["id"], candidates[row["id"]]) for row in valid]
        return sorted(hits, key=lambda hit: (-hit.score, str(hit.chunk_id)))[:limit]

    def deactivate_revision(self, revision_id: UUID) -> None:
        # Commit the exclusion first. A failed remote delete remains safe and retryable.
        with psycopg.connect(self.dsn) as connection:
            connection.execute(
                "UPDATE ai_tutor.ai_source_revisions SET state = 'INACTIVE', "
                "deactivated_at = now(), updated_at = now() WHERE id = %s",
                (revision_id,),
            )
        self._post("/vectors/delete", {
            "namespace": self.namespace,
            "filter": {"revision_id": {"$eq": str(revision_id)}},
        })

    def health(self) -> bool:
        try:
            with psycopg.connect(self.dsn) as connection:
                connection.execute("SELECT 1").fetchone()
            response = self.client.post(
                self.host + "/describe_index_stats", json={}, headers=self.headers)
            response.raise_for_status()
            payload = response.json()
            return isinstance(payload, dict) and payload.get("dimension") == self.dimension
        except (psycopg.Error, httpx.HTTPError, ValueError):
            return False
