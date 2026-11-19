# ADR-0013: Index readiness states

- **Status:** Accepted
- **Decision date:** 2026-10-22
- **Decision owner(s):** M7 project owner
- **Related ADRs:** [0003 Content parsing](0003-content-parsing-and-chunking.md), [0006 Indexing delivery](0006-indexing-event-delivery.md)

## Context

The UI needs to explain when course-grounded answers are possible. A single boolean does not distinguish no eligible sources, active indexing, partial availability or failures. M7 exposes counts for ready, indexing and failed eligible sources.

## Decision drivers

- Tell the client whether any source is queryable.
- Distinguish work in progress from no eligible material or failed indexing.
- Make partial availability visible.
- Avoid implying that all course materials are indexed when only one is ready.

## Options considered

### Option A: Boolean `ready`

- Benefit: small response.
- Cost: hides whether work is pending or failed.

### Option B: Four states plus source counts

- Benefit: expresses partial readiness and diagnostic counts.
- Cost: callers must handle a small state machine.

## Proposal or decision

**Accepted:** use `NOT_READY`, `INDEXING`, `READY`, and `DEGRADED`. `NOT_READY` means zero queryable and zero pending/processing; zero counts means no eligible sources, while failed counts indicate failed indexing. `INDEXING` means zero queryable and at least one pending/processing. `READY` means every eligible source has a current queryable revision. `DEGRADED` means at least one source is queryable while another eligible source is pending, processing or failed. If no source is queryable, chat returns `409 AI_INDEX_NOT_READY`.

## Consequences

The `ready_sources`, `indexing_sources` and `failed_sources` counts must use the same source eligibility rules as retrieval. The UI can display partial availability; it should not claim all sources are ready for `DEGRADED`.

## Revisit when

Revisit if the UI or operations team needs to distinguish “no eligible content” from “all indexing attempts failed” as separate enum values.

## References

- [M7 status API and readiness rules](../docs/m7-ai-tutor.md)
- [M7 OpenAPI status schema](../openapi/m7-ai-tutor.yaml)
