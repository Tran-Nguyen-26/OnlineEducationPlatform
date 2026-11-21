"""Resolve vector matches against current, authorized M7 source rows."""

from __future__ import annotations

from dataclasses import dataclass
from uuid import UUID

import psycopg

from oep_ai_tutor.embeddings import EmbeddingProvider
from oep_ai_tutor.vector_store import VectorStore


@dataclass(frozen=True)
class Evidence:
    chunk_id: UUID
    revision_id: UUID
    content_id: UUID
    course_id: UUID
    lesson_id: UUID
    version_number: int
    source_title: str
    text: str
    page_number: int | None
    heading: str | None
    json_path: str | None
    score: float

    def citation(self) -> dict:
        return {"chunk_id": self.chunk_id, "content_id": self.content_id,
                "lesson_id": self.lesson_id, "source_title": self.source_title,
                "excerpt": self.text[:1000], "version_number": self.version_number,
                "page_number": self.page_number, "heading": self.heading,
                "json_path": self.json_path}


CURRENT_SOURCE = (
    "revision.state = 'READY' AND revision.deactivated_at IS NULL "
    "AND revision.course_id = chunk.course_id "
    "AND revision.lesson_id = chunk.lesson_id "
    "AND revision.embedding_space_key = chunk.embedding_space_key "
    "AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_source_revisions AS newer "
    "WHERE newer.content_id = revision.content_id "
    "AND newer.embedding_space_key = revision.embedding_space_key "
    "AND newer.version_number > revision.version_number) "
    "AND NOT EXISTS (SELECT 1 FROM ai_tutor.ai_index_jobs AS newer_job "
    "WHERE newer_job.content_id = revision.content_id "
    "AND (newer_job.version_number > revision.version_number "
    "OR (newer_job.version_number = revision.version_number "
    "AND newer_job.action = 'DEACTIVATE')))"
)


def has_ready_source(connection: psycopg.Connection, *, course_id: UUID,
                     space_key: str, dimension: int) -> bool:
    row = connection.execute(
        "SELECT EXISTS (SELECT 1 FROM ai_tutor.ai_source_chunks AS chunk "
        "JOIN ai_tutor.ai_source_revisions AS revision "
        "ON revision.id = chunk.source_revision_id "
        "WHERE chunk.course_id = %s AND chunk.embedding_space_key = %s "
        "AND chunk.embedding_dimension = %s AND " + CURRENT_SOURCE + ") AS ready",
        (course_id, space_key, dimension),
    ).fetchone()
    return bool(row["ready"])


def current_evidence(connection: psycopg.Connection, *, chunk_ids: list[UUID],
                     course_id: UUID, lesson_ids: tuple[UUID, ...],
                     space_key: str, dimension: int, lock: bool = False) -> dict[UUID, dict]:
    if not chunk_ids or not lesson_ids:
        return {}
    rows = connection.execute(
        "SELECT chunk.id AS chunk_id, revision.id AS revision_id, "
        "revision.content_id, revision.version_number, revision.source_title, "
        "chunk.course_id, chunk.lesson_id, chunk.chunk_text, chunk.page_number, "
        "chunk.heading, chunk.json_path "
        "FROM ai_tutor.ai_source_chunks AS chunk "
        "JOIN ai_tutor.ai_source_revisions AS revision ON revision.id = chunk.source_revision_id "
        "WHERE chunk.id = ANY(%s) AND chunk.course_id = %s "
        "AND chunk.lesson_id = ANY(%s) AND chunk.embedding_space_key = %s "
        "AND chunk.embedding_dimension = %s AND " + CURRENT_SOURCE +
        (" FOR SHARE OF revision, chunk" if lock else ""),
        (chunk_ids, course_id, list(lesson_ids), space_key, dimension),
    ).fetchall()
    return {row["chunk_id"]: row for row in rows}


def retrieve(connection: psycopg.Connection, *, store: VectorStore,
             embedding: EmbeddingProvider, course_id: UUID,
             lesson_ids: tuple[UUID, ...], space_key: str, query: str,
             preferred_lesson_id: UUID | None = None, limit: int = 8) -> list[Evidence]:
    if not space_key or not query.strip() or not 1 <= limit <= 20:
        raise ValueError("Retrieval requires a space, question and bounded limit")
    if preferred_lesson_id is not None and preferred_lesson_id not in lesson_ids:
        raise ValueError("Preferred lesson is outside the signed lesson scope")
    if not lesson_ids:
        return []
    vector = embedding.embed_query(query)
    matches = store.query(course_id=course_id, lesson_ids=lesson_ids,
                          space_key=space_key, vector=vector, limit=limit)
    if not matches:
        return []
    ids = [match.chunk_id for match in matches]
    if len(ids) != len(set(ids)):
        raise ValueError("Vector store returned duplicate chunk IDs")
    rows = current_evidence(connection, chunk_ids=ids, course_id=course_id,
                            lesson_ids=lesson_ids, space_key=space_key,
                            dimension=embedding.dimension)
    evidence = [Evidence(match.chunk_id, row["revision_id"], row["content_id"],
                         row["course_id"], row["lesson_id"], row["version_number"],
                         row["source_title"], row["chunk_text"], row["page_number"],
                         row["heading"], row["json_path"], match.score)
                for match in matches if (row := rows.get(match.chunk_id)) is not None]
    if preferred_lesson_id is not None:
        evidence.sort(key=lambda item: item.lesson_id != preferred_lesson_id)
    return evidence
