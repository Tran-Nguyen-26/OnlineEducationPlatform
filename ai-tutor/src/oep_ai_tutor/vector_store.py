"""Store-neutral vector IDs and scope for M7 retrieval/ingestion."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol
from uuid import UUID


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
