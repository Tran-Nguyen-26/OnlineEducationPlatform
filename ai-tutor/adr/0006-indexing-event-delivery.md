# ADR-0006: Content-indexing event delivery

- **Status:** Proposed
- **Decision date:** Pending
- **Decision owner(s):** M2/M7 project team
- **Related ADRs:** [0001 Vector database](0001-vector-database.md), [0010 Content invalidation](0010-content-invalidation-handshake.md)

## Context

M2 content changes must trigger asynchronous M7 indexing. M2 documentation proposes a transactional outbox but has not settled event names or delivery. The backend already runs RabbitMQ in Docker Compose. M7 currently specifies an internal HTTP enqueue endpoint as an alternative. Content changes and indexing failures must be retryable and deduplicated.

## Decision drivers

- Do not lose an indexing event when M2 commits a content change.
- Handle duplicate and out-of-order events by content version.
- Reuse existing project infrastructure where appropriate.
- Keep the private M7 service off the public network.

## Options considered

### Option A: M2 transactional outbox to RabbitMQ, consumed by M7

- Benefits: event intent can be committed with M2's content transaction; uses the existing broker in development.
- Costs and risks: requires outbox publishing, broker operations and event-schema ownership.

### Option B: M2 calls M7 internal HTTP enqueue

- Benefits: simple interface and visible job acknowledgement.
- Costs and risks: M2 must manage retry/outbox behavior or risk losing calls across failures; synchronous dependency on M7 availability.

### Option C: M7 polls M2 for changes

- Benefits: M2 does not push events to M7.
- Costs and risks: polling delay, repeated scans and more complex change tracking.

## Proposal or decision

**Proposal only:** use M2 transactional outbox with RabbitMQ for UPSERT/index scheduling, with stable event IDs and content version numbers. Keep HTTP enqueue as an operational/demo fallback, not a second source of truth. This does not replace the synchronous deletion acknowledgement in ADR-0010.

## Consequences

M2 owns event creation and delivery; M7 owns idempotent job processing and dead-letter/retry handling. The event must contain identifiers and action, not arbitrary object URLs or storage keys. M7 loads authoritative metadata and private file grants from M2.

## Revisit when

Revisit if the team selects a managed queue instead of RabbitMQ or M2's outbox is explicitly deferred.

## References

- [Backend Docker Compose](../../backend/docker-compose.yml)
- [Current backend outbox design](../../backend/DATABASE.md)
- [M7 integration proposal](../docs/m7-ai-tutor.md)
