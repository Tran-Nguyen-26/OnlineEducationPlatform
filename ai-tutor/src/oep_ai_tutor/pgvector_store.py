"""Exact course/lesson/space-scoped pgvector search on current READY sources."""

from __future__ import annotations

import math
from uuid import UUID

import psycopg
from psycopg.rows import dict_row
from pgvector import Vector
from pgvector.psycopg import register_vector

from oep_ai_tutor.vector_store import VectorMatch, VectorRecord


def _checked_vector(values: list[float]) -> Vector:
    if not values:
        raise ValueError("Vector values must be finite nonzero numbers")
    checked: list[float] = []
    for value in values:
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise ValueError("Vector values must be finite nonzero numbers")
        try:
            number = float(value)
        except OverflowError as exc:
            raise ValueError("Vector values must be finite nonzero numbers") from exc
        if not math.isfinite(number):
            raise ValueError("Vector values must be finite nonzero numbers")
        checked.append(number)
    if not any(checked):
        raise ValueError("Cosine search requires a nonzero vector")
    return Vector(checked)


class PgvectorStore:
    def __init__(self, dsn: str):
        if not dsn:
            raise ValueError("PostgreSQL DSN is required")
        self.dsn = dsn

    def _connect(self) -> psycopg.Connection:
        connection = psycopg.connect(self.dsn, row_factory=dict_row)
        register_vector(connection)
        return connection

    def upsert(self, record: VectorRecord) -> None:
        if not record.space_key:
            raise ValueError("Embedding space is required")
        embedding = _checked_vector(record.values)
        with self._connect() as connection:
            row = connection.execute(
                "UPDATE ai_tutor.ai_source_chunks AS chunk SET embedding = %s "
                "WHERE chunk.id = %s AND chunk.source_revision_id = %s "
                "AND chunk.course_id = %s AND chunk.lesson_id = %s "
                "AND chunk.embedding_space_key = %s AND chunk.embedding_dimension = %s "
                "AND EXISTS (SELECT 1 FROM ai_tutor.ai_source_revisions AS revision "
                "WHERE revision.id = chunk.source_revision_id "
                "AND revision.state IN ('PENDING', 'PROCESSING', 'READY')) RETURNING chunk.id",
                (embedding, record.chunk_id, record.revision_id, record.course_id,
                 record.lesson_id, record.space_key, len(record.values)),
            ).fetchone()
            if row is None:
                raise ValueError("Chunk is missing, inactive or has different scope/dimension")

    def query(self, *, course_id: UUID, lesson_ids: tuple[UUID, ...], space_key: str,
              vector: list[float], limit: int) -> list[VectorMatch]:
        if not space_key or not 1 <= limit <= 100:
            raise ValueError("Embedding space and bounded positive limit are required")
        embedding = _checked_vector(vector)
        if not lesson_ids:
            return []
        with self._connect() as connection:
            rows = connection.execute(
                "WITH eligible AS MATERIALIZED ("
                " SELECT chunk.id, chunk.embedding FROM ai_tutor.ai_source_chunks AS chunk "
                " JOIN ai_tutor.ai_source_revisions AS revision "
                " ON revision.id = chunk.source_revision_id "
                " WHERE chunk.course_id = %s AND chunk.lesson_id = ANY(%s) "
                " AND chunk.embedding_space_key = %s AND chunk.embedding_dimension = %s "
                " AND chunk.embedding IS NOT NULL AND revision.state = 'READY' "
                " AND revision.deactivated_at IS NULL "
                " AND revision.course_id = %s AND revision.lesson_id = chunk.lesson_id "
                " AND revision.embedding_space_key = %s "
                " AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_course_access AS access "
                " WHERE access.course_id = revision.course_id AND access.active = false)"
                " AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_source_revisions AS newer "
                " WHERE newer.content_id = revision.content_id "
                " AND newer.embedding_space_key = revision.embedding_space_key "
                " AND newer.version_number > revision.version_number)"
                " AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_index_jobs AS newer_job "
                " WHERE newer_job.content_id = revision.content_id "
                " AND (newer_job.version_number > revision.version_number "
                " OR (newer_job.version_number = revision.version_number "
                " AND newer_job.action = 'DEACTIVATE')))"
                ") SELECT id, 1 - (embedding <=> %s) AS score FROM eligible "
                "ORDER BY embedding <=> %s, id LIMIT %s",
                (course_id, list(lesson_ids), space_key, len(vector), course_id,
                 space_key, embedding, embedding, limit),
            ).fetchall()
        return [VectorMatch(row["id"], float(row["score"])) for row in rows]

    def deactivate_revision(self, revision_id: UUID) -> None:
        with self._connect() as connection:
            connection.execute(
                "UPDATE ai_tutor.ai_source_revisions SET state = 'INACTIVE', "
                "deactivated_at = now(), updated_at = now() WHERE id = %s",
                (revision_id,),
            )
            connection.execute(
                "UPDATE ai_tutor.ai_source_chunks SET embedding = NULL "
                "WHERE source_revision_id = %s", (revision_id,),
            )

    def health(self) -> bool:
        try:
            with self._connect() as connection:
                return connection.execute("SELECT 1 FROM pg_extension WHERE extname = 'vector'").fetchone() is not None
        except psycopg.Error:
            return False
