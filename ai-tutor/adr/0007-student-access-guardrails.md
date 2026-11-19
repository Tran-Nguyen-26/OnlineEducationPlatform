# ADR-0007: Student access, quiz and content guardrails

- **Status:** Accepted for first release
- **Decision date:** 2026-11-19
- **Decision owner(s):** Product/course team
- **Related ADRs:** [0010 Content invalidation](0010-content-invalidation-handshake.md)

## Context

M7's first-release product scope is Student-only, with a `PUBLISHED` course and `ACTIVE` enrollment; `COMPLETED` is denied. An earlier M7 draft proposed a per-lesson AI switch. The team explicitly removed that switch for the first release: eligible `AVAILABLE` learning content is indexed automatically. The team kept the lock for an `IN_PROGRESS` quiz in the same course. Instructors and Admin never receive access to M7 chat, status or conversation history.

## Decision drivers

- Respect enrollment and course lifecycle authorization on every request.
- Prevent AI use during an active assessment if this is a course policy.
- Reuse M2's existing publication and content-availability rules to determine evidence eligibility.
- Keep M7 from owning a duplicate authorization database.

## Options considered

### Option A: Add a per-lesson AI switch

- Benefits: course managers could opt individual lessons out of AI evidence.
- Costs and risks: adds an M2 field, management flow and default-state ambiguity; can leave the tutor with no sources even when learning content is available.

### Option B: Enrollment and course lifecycle only

- Benefits: smaller integration surface and lower per-request dependency count.
- Costs and risks: does not enforce the agreed active-quiz restriction.

### Option C: Quiz-context blocking with automatic source eligibility

- Benefits: addresses active assessments and uses M2's existing publication/content-availability state, with no extra Admin task.
- Costs and risks: still requires assessment-state integration; all eligible content in the course can be used as evidence.

## Proposal or decision

**Accepted first-release policy:** M7 chat, status and conversation/history endpoints are Student-only. Require a `PUBLISHED` course and `ACTIVE` enrollment; deny `COMPLETED`, `WITHDRAWN`, `REMOVED`, archived and disabled access. Block AI chat and history while the Student has an `IN_PROGRESS` quiz in the same course using authoritative assessment state. There is no per-lesson AI switch. Only current, eligible `AVAILABLE` learning content of the authorized `PUBLISHED` course may provide retrieval evidence. Spring checks authoritative M3/assessment/M2 policy on each request and fails closed if a required check is unavailable. Do not accept client-supplied eligibility flags.

## Consequences

The final policy changes the gateway checks, M2/M3 contracts, status display and acceptance cases. M2 needs content-change integration for indexing and invalidation, but no AI-specific lesson setting or management permission. Instructors and Admin have no M7 API access.

## Revisit when

Revisit if the team later changes completed-student course-content access, the quiz policy or evidence eligibility.

## References

- [M7 authorization matrix](../docs/m7-ai-tutor.md)
- [Current backend business rules](../../backend/BUSINESS.md)
- [Current backend enrollment schema](../../backend/DATABASE.md)
