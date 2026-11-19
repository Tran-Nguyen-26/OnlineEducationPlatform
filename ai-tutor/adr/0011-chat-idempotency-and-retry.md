# ADR-0011: Chat idempotency and retry

- **Status:** Accepted
- **Decision date:** 2026-10-22
- **Decision owner(s):** M7 project owner
- **Related ADRs:** [0008 Conversation retention](0008-conversation-retention.md)

## Context

Clients may retry a send after a network timeout. Duplicate generation can create duplicate turns and charges. The API uses a client-generated UUID `Idempotency-Key` for each send.

## Decision drivers

- At most one committed user/assistant turn per key.
- Safe retries after transient pre-commit errors.
- Same key with another payload must not silently change the request.
- Avoid claiming exactly-once execution or provider billing.

## Options considered

### Option A: Best-effort deduplication in memory

- Benefit: easy prototype.
- Cost: fails across restarts and concurrent replicas.

### Option B: Durable request ledger and atomic commit

- Benefit: survives restarts and coordinates concurrent requests.
- Cost: additional persistence and explicit retry states.

## Proposal or decision

**Accepted:** persist a unique ledger key `(user_id, conversation_id, idempotency_key)` with normalized payload hash and state. Same key and payload after completion replays the stored response; changed payload returns `409 IDEMPOTENCY_CONFLICT`; an in-progress duplicate returns `409 AI_REQUEST_IN_PROGRESS`. A transient failure before turn commit is retryable with the same key. Commit both messages and request completion atomically. Check current authorization before replaying a cached result.

## Consequences

An ambiguous upstream timeout may cause another provider charge on retry, but the database must still prevent a second committed turn. Validation and authorization failures occur before the ledger is claimed. Keep bounded retry metadata and terminal failure details without storing secrets.

## Revisit when

Revisit if the product changes request semantics, supports streaming, or requires a different client retry contract.

## References

- [M7 idempotency contract](../docs/m7-ai-tutor.md)
- [M7 send-message OpenAPI](../openapi/m7-ai-tutor.yaml)
