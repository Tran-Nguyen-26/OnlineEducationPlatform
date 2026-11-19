# ADR-0010: M2-M7 content invalidation handshake

- **Status:** Accepted
- **Decision date:** 2026-10-22
- **Decision owner(s):** M2/M7 implementer (project owner)
- **Related ADRs:** [0001 Vector database](0001-vector-database.md), [0006 Indexing delivery](0006-indexing-event-delivery.md)

## Context

M2 owns source versions and availability; M7 stores derived chunks. An asynchronous delete event by itself can arrive after a stale chunk has already been retrieved. The same issue applies when a source becomes unavailable or a new version replaces an old one.

## Decision drivers

- Never retrieve a source after M2 has revoked it.
- Keep M2 authoritative for content lifecycle.
- Recover safely when M7 is unavailable.
- Make retries idempotent.

## Options considered

### Option A: Asynchronous invalidation after M2 deletion

- Benefit: deletion is not blocked on M7.
- Cost: stale chunks can remain queryable during delivery delay or failure.

### Option B: Synchronous acknowledgement handshake

- Benefit: M2 finalizes only after M7 confirms the source is no longer queryable.
- Cost: deletion can remain pending while M7 is unavailable.

## Proposal or decision

**Accepted:** M2 first marks the source unavailable so new student reads and indexing stop. M2 synchronously requests M7 to deactivate the specific content revision and waits for acknowledgement. M2 finalizes deletion/replacement only after acknowledgement. On timeout or M7 failure, leave the source unavailable and operation pending/retryable; do not finalize while stale chunks are queryable. Deactivation is idempotent.

## Consequences

M2 needs a pending/deleting lifecycle state and retry handling. The private M7 endpoint returns success only after the revision is excluded from retrieval. This is a separate synchronous invalidation path from asynchronous UPSERT indexing. Old versions cannot be returned after M2 activates a newer current version.

## Revisit when

Revisit only if the project adopts a stronger transactional cross-service mechanism that preserves the same no-stale-retrieval guarantee.

## References

- [M7 invalidation protocol](../docs/m7-ai-tutor.md)
- [M7 internal API](../openapi/m7-ai-tutor.yaml)
- [Live M2 lesson-content deletion service](../../backend/core-lms-service/src/main/java/com/oep/corelms/service/AdminLessonContentService.java)
