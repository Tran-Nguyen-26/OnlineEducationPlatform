"""Store-neutral vector IDs and scope for M7 retrieval/ingestion."""

from __future__ import annotations

from dataclasses import dataclass
import os
from typing import Protocol
from uuid import UUID

import httpx


@dataclass(frozen=True)
class VectorRecord:
    chunk_id: UUID
    revision_id: UUID
    course_id: UUID
    lesson_id: UUID
    space_key: str
    values: list[float]


@dataclass(frozen=True)
class VectorMatch:
    chunk_id: UUID
    score: float


class VectorStore(Protocol):
    def upsert(self, record: VectorRecord) -> None: ...

    def query(self, *, course_id: UUID, lesson_ids: tuple[UUID, ...], space_key: str,
              vector: list[float], limit: int) -> list[VectorMatch]: ...

    def deactivate_revision(self, revision_id: UUID) -> None: ...

    def health(self) -> bool: ...


def vector_store_from_env(*, client: httpx.Client | None = None,
                          environ: dict[str, str] | None = None) -> VectorStore:
    """Select the vector backend once at the composition boundary."""
    settings = os.environ if environ is None else environ
    dsn = settings.get("AI_TUTOR_DATABASE_URL", "")
    kind = settings.get("VECTOR_STORE", "pgvector").lower()
    if kind == "pgvector":
        from oep_ai_tutor.pgvector_store import PgvectorStore
        return PgvectorStore(dsn)
    if kind == "pinecone":
        from oep_ai_tutor.pinecone_store import PineconeStore
        dimension = int(settings.get("AI_TUTOR_EMBEDDING_DIMENSION", "1024"))
        return PineconeStore(
            dsn, host=settings.get("PINECONE_INDEX_HOST", ""),
            api_key=settings.get("PINECONE_API_KEY", ""),
            namespace=settings.get("PINECONE_NAMESPACE", "ai-tutor"),
            dimension=dimension,
            client=client or httpx.Client(timeout=10.0),
        )
    raise ValueError("Unsupported VECTOR_STORE")
