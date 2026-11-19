# M7 AI Tutor Architecture Decision Records

This folder records the decisions that shape M7 AI Tutor so the team can explain and revisit them later. Each ADR uses [the shared template](0000-template.md).

`Proposed` means the options and recommendation are documented but the decision owner has not approved one. `Accepted` means the choice has been agreed for implementation. `Temporarily accepted` means use the choice for the current milestone and revisit it when the named common standard or constraint is available.

## ADR index

| ADR | Topic | Status |
| --- | --- | --- |
| [0001](0001-vector-database.md) | Vector database and storage boundary | Accepted: temporary pgvector default, both adapters |
| [0002](0002-embedding-model-and-dimensions.md) | Embedding model and dimensions | Accepted: initial Jina model, provider-neutral adapters |
| [0003](0003-content-parsing-and-chunking.md) | Content parsing and chunking | Accepted formats; chunking parameters proposed |
| [0004](0004-retrieval-and-ranking.md) | Retrieval, ranking and abstention | Proposed |
| [0005](0005-generation-model-provider.md) | Generation model and provider | Accepted: initial Groq model, three adapters |
| [0006](0006-indexing-event-delivery.md) | Indexing event delivery | Proposed |
| [0007](0007-student-access-guardrails.md) | Student access, quiz and content guardrails | Accepted: Student-only M7; no per-lesson AI switch |
| [0008](0008-conversation-retention.md) | Conversation retention and deletion | Proposed |
| [0009](0009-service-to-service-identity.md) | Gateway-to-M7 service identity | Proposed |
| [0010](0010-content-invalidation-handshake.md) | M2-M7 content invalidation handshake | Accepted |
| [0011](0011-chat-idempotency-and-retry.md) | Chat idempotency and retry | Accepted |
| [0012](0012-public-api-conventions.md) | Public API conventions | Accepted for first release |
| [0013](0013-index-readiness-states.md) | Index readiness states | Accepted |
| [0014](0014-model-selection.md) | ADR-AI-001: initial Groq/Jina configuration; historical shortlist for later evaluation | Accepted initial configuration |

ADRs 0003, 0004, 0006, 0008 and 0009 still contain implementation details or later non-functional proposals. Accepted ADRs record the team's confirmed first-release choices; a configured provider, model or vector store can still be changed through its adapter boundary, with data migration where required.
