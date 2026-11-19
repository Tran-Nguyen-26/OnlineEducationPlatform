# ADR-0012: Public API conventions

- **Status:** Accepted for first release; pagination/error format may be revisited with a common standard
- **Decision date:** 2026-11-19
- **Decision owner(s):** M7 project owner
- **Related ADRs:** None

## Context

The live Java gateway and M2 controllers use `/api/courses/**`, while the older M7 draft used `/api/v1/courses/**`. The agreed M7 prefix is `/api/courses/{courseId}/ai/...` to fit deployed routing. The first-release contract keeps 1-based `page`, `page_size`, and an error envelope containing `code`, `message`, `request_id` and `details`; verify these against implemented backend behavior. M3 currently uses a different pagination and error format.

## Decision drivers

- Keep public M7 consistent with M2 for the current milestone.
- Avoid forcing an unrelated M3 contract change.
- Preserve a single client-facing error format.

## Options considered

### Option A: Use M2 conventions for public M7

- Benefit: consistent with M2 and current gateway patterns.
- Cost: M3 differs and must be adapted internally.

### Option B: Use M3 conventions

- Benefit: consistency with enrollment implementation.
- Cost: diverges from M2 and requires client/gateway coordination.

### Option C: Wait for a common standard

- Benefit: avoids temporary divergence.
- Cost: blocks the M7 public contract.

## Proposal or decision

**Accepted for the first release:** public M7 uses `/api/courses/{courseId}/ai/...` to match the live gateway, with 1-based `page`, `page_size`, and the M2-style error envelope. The gateway translates internal M3 checker errors. Revisit the format only when a common API standard is published.

## Consequences

M7 clients use one provisional convention. This decision does not alter M3's own API. Keep HTTP status and error-code mapping in the gateway contract.

## Revisit when

The common gateway team approves a shared route, pagination and error standard.

## References

- [M7 OpenAPI](../openapi/m7-ai-tutor.yaml)
- [Live gateway routes](../../backend/api-gateway/src/main/resources/application.yml)
- [Current backend API overview](../../backend/README.md)
