# ADR-0008: Conversation retention and deletion

- **Status:** Proposed
- **Decision date:** Pending
- **Decision owner(s):** Product/privacy team
- **Related ADRs:** [0011 Chat idempotency](0011-chat-idempotency-and-retry.md)

## Context

Conversations contain student questions, AI responses and source excerpts. The API currently proposes logical deletion, but the physical retention period and behavior for inactive chats are not approved. A user can lose course access while the system retains history; retention must never grant access to read it.

## Decision drivers

- Minimize retention of student data and source excerpts.
- Preserve useful learning history for returning students.
- Support user-initiated deletion and reliable backup expiry.
- Make retention behavior understandable to students and implementable by the team.

## Options considered

### Option A: Retain until the user deletes or the account closes

- Benefits: history remains available for long-term learning.
- Costs and risks: indefinite retention and larger privacy/security exposure.

### Option B: Inactivity expiry plus shorter deletion grace period

- Benefits: bounds stale data while allowing a short recovery window after user deletion.
- Costs and risks: users may lose older history; purge and backup jobs must be reliable.

### Option C: Short fixed retention for all conversations

- Benefits: strongest minimization and simple retention rule.
- Costs and risks: can remove useful active-course history unexpectedly.

## Proposal or decision

**Proposal only:** hide user-deleted chats immediately and purge them within 30 days; purge chats after 12 months without activity. Keep history inaccessible while course entitlement is absent, even during the retention period. Confirm account-deletion behavior, source-deletion behavior, legal/privacy constraints and backup purge limits before adopting these durations.

## Consequences

Deletion must remove chat content from future prompts immediately. Define whether citations/excerpts are redacted when their source is deleted and how backups age out. Publish the final period in the product privacy notice before implementation.

## Revisit when

Revisit after privacy/legal review, user feedback or the team has measured storage growth.

## References

- [M7 conversation lifecycle](../docs/m7-ai-tutor.md)
- [M7 OpenAPI delete endpoint](../openapi/m7-ai-tutor.yaml)
