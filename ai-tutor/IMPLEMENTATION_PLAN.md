# M7 AI Tutor implementation plan

## Delivery order and status

Implement **functional requirements first** (F1–F6). Keep non-functional work (N1–N4) in a separate later phase. All checkboxes remain open until code and relevant checks pass; the existing Java AI entities and baseline tables are scaffolding, not a working tutor. Security rules that determine who may see course material, citation validity, version invalidation, and retry correctness are functional acceptance criteria and stay in the first phase.

The M7 design and OpenAPI describe intended behavior, not implemented endpoints. Before changing Java integration, read the relevant backend documentation (`backend/README.md`, `BUSINESS.md`, `DATABASE.md`, `FLOWS.md`, `STATUS.md`, gateway configuration and affected M2/M3/assessment controllers/services), then compare the live code with the M7 contract. Ask the project owner about any product or cross-service contract that remains ambiguous. Preserve existing Java behavior outside M7.

## Confirmed first-release decisions

- Python/FastAPI is a **separate private service**. Spring Boot owns public JWT authentication and authoritative course/enrollment/quiz checks, then calls the documented M7 internal API. React never calls FastAPI directly. The Java client and Python handlers must be developed against the same internal contract.
- **pgvector is the temporary deployment default.** Implement **both pgvector and Pinecone adapters** behind one `VectorStore` interface; select via configuration, not RAG/chat conditionals. Keep M7-owned tables/migrations separate from Java/Hibernate ownership. Changing the store later requires moving/re-indexing vectors and verifying source consistency, but must not change RAG logic or HTTP contracts.
- Start answer generation with Groq model `openai/gpt-oss-120b`. Implement provider-neutral generation and three adapters: Groq, OpenAI and Gemini. Provider/model IDs, credentials and limits are configuration, not business logic. Changing provider must not change the public or internal M7 API.
- The initial embedding provider/model is **Jina AI `jina-embeddings-v5-text-small`**, full 1024 dimensions with retrieval query/passage task modes. Implement a provider-neutral `EmbeddingProvider` interface and Jina, OpenAI and Gemini adapters, selected by configuration. Changing the embedding space requires re-indexing even when application APIs remain stable. Verify Jina account/model terms before production use.
- The first release uses one **JSON** chat response; SSE stays a later functional enhancement. Access is Student-only with `ACTIVE` enrollment. Block AI while the Student has a quiz `IN_PROGRESS` in the same course. There is no per-lesson AI switch: eligible `AVAILABLE` learning content in a `PUBLISHED` course is indexed automatically. Instructors and Admin do **not** receive M7 chat, status or conversation-history access. Public paths use `/api/courses/{courseId}/ai/...`, matching the live gateway; foreign conversation IDs return 404.
- Supported learning content: inline `TEXT`, text-based `PDF`, Markdown (`.md`) and JSON (`.json`). Map `.md`/`.json` Admin-uploaded learning materials through M2 `FILE` with a MIME/extension allowlist. Flatten JSON scalar leaves to readable `path: value` text and retain paths for citations; do not ingest arbitrary user/account JSON. Scanned PDFs get an unsupported/OCR-required state; OCR is outside this phase.

## Phase F — Functional requirements first

### F1. Contract and service boundary

- [ ] Finalize the agreed public `/api/courses/{courseId}/ai/...` contract and the private Spring Boot → FastAPI API: exact paths, methods, request/response schemas, service authentication, error mapping and request IDs. Publish the private contract for the Java team before implementing either side.
- [ ] Create an independent Python/FastAPI service and Java client/gateway integration. Spring derives user identity from JWT, checks Student/course entitlement and quiz policy, sends a signed short-lived user/course assertion to the private service; Python verifies it and never trusts client-supplied identity headers.
  - [x] Bootstrap the independent FastAPI package with a locked `uv` environment and fail-closed HS256 student-assertion verifier. Verify issuer/audience/scope, UUID claims, 60-second lifetime, path course and request ID with focused unit and HTTP-dependency tests. The Java issuer, authorized proxy endpoints and indexing scope remain open.
  - [x] Add the conditional core LMS student-assertion issuer with a dedicated secret, exact claim set and 60-second TTL. Verify signature/claims, unique token IDs, invalid secrets and disabled-by-default configuration in Java tests; rerun the Python verifier tests. Public authorization/proxy endpoints and indexing scope remain open.
  - [x] Add a primary-routed core LMS access policy for Student role, `PUBLISHED` course, `ACTIVE` enrollment, non-upcoming content and no same-course `IN_PROGRESS` quiz. Test denial and acceptance branches. Per-student unlocked-lesson scope and the public/private proxy are still open.
  - [x] Extract a shared M2 lesson-access service for `SEQUENTIAL` course locks, use it for protected content and the Student outline, and expose a per-enrollment readable-lesson ID list for the future AI proxy. Verify locked content stays hidden and lessons unlock in order. Transporting that list to M7 and enforcing it inside vector search remain open.
  - [x] Define and test the signed per-request lesson-scope digest on both sides: Spring can bind its readable-lesson IDs to a student assertion, and Python validates the private chat body's IDs against that digest. The Java proxy must still send the scope, and M7 must enforce it inside retrieval.
  - [x] Add the conditional Java public proxy for status, conversation CRUD/history and JSON send-message. Every route checks the current Student/course/quiz policy, sends a fresh internal assertion and request ID, and maps recognized M7 errors to Spring's public ProblemDetail response. Send-message binds the current readable-lesson scope and forwards the same UUID-v4 idempotency key. Focused transport tests and the full backend suite, including Docker-backed Flyway migration tests, pass. Private Python handlers, vector scope enforcement and end-to-end chat remain open.
- [x] Decide AI-owned relational tables and migration ownership. M7 owns a separate `ai_tutor` schema and its migrations; unused Java AI mappings are removed, while historical `public.ai_*` tables in immutable `V1__baseline.sql` remain untouched pending a data audit. See ADR 0015. The first M7 migration and its schema tests remain open under F2.

### F2. Eligible content and indexing

- [ ] Integrate `TEXT` and PDF plus `.md`/`.json` Admin uploads through M2 `FILE`; implement size and MIME/extension rules, and convert JSON scalar leaves into readable `path: value` text with stable paths. Index eligible `AVAILABLE` content in a `PUBLISHED` course automatically, with no per-lesson AI flag; confirm any further live M2 visibility checks.
- [ ] Define M2 → M7 versioned enqueue/event contract with stable event ID, `content_id`, `course_id`, `lesson_id`, `version_number` and UPSERT/deactivation action. Enqueue existing eligible content on course publication and deny M7 access immediately when a course stops being `PUBLISHED`. Implement the agreed synchronous M2 → M7 deactivation acknowledgement for content deletion/replacement; `AdminLessonContentService.delete` currently deletes directly and must be integrated with this flow before indexed content can be removed safely.
- [ ] Implement asynchronous parsing/chunking/embedding for the four supported formats with source, lesson, page/heading/path and version provenance. Mark scanned/empty PDFs unsupported; expose Admin-visible processing state through an M2 management route, without granting Admin access to M7 endpoints.
- [ ] Persist source revisions and jobs in M7-owned tables. Define a store-neutral `VectorStore` contract for upsert, course-filtered query, revision deactivation/delete and health; implement **pgvector and Pinecone adapters**, selected by `VECTOR_STORE` configuration. Record the embedding-space key and dimension, index/query only within the active space, and re-embed into a new compatible index when that space changes. Both adapters must return normalized chunk IDs/scores and pass the same isolation/version tests. Make duplicate/out-of-order jobs idempotent; only current `READY` revisions are queryable. Do not acknowledge deactivation until old evidence is excluded from answers.
- [ ] Expose `NOT_READY`, `INDEXING`, `READY`, `DEGRADED` status and counts; when no source is queryable, return `409 AI_INDEX_NOT_READY`.

### F3. Course-isolated retrieval and grounded answers

- [ ] Implement `retrieve(courseId, lessonId, query)` with mandatory verified `course_id`, per-student unlocked-lesson scope, eligible-content and current-source constraints inside vector search; validate returned IDs against authoritative M7 source state. Prefer the current lesson when supplied. Never use another course, locked lesson or unavailable content as fallback.
- [ ] Ground each answer in returned chunks, validate cited IDs server-side, show source title/excerpt/page or JSON path when available, and abstain when evidence is missing. A citation link obtains a fresh protected access URL from M2.
- [ ] Use separate system instructions and untrusted retrieved context. Do not let source text override permissions, citation validation or course scope.

### F4. Conversations and provider adapters

- [ ] Implement private FastAPI endpoints for status, create/list/read/delete own conversations, list messages and send a turn; Spring exposes the agreed `/api/courses/{courseId}/ai/...` routes. Keep conversation owner and course fixed and recheck access on every request.
- [ ] Implement a provider-neutral `GenerationProvider` interface and Groq, OpenAI and Gemini adapters. Normalize answer text, usage, errors and optional token events; select provider and model via configuration. Start the configured Groq model without hardcoding it in RAG or API layers.
- [ ] Implement a provider-neutral `EmbeddingProvider` interface with Jina, OpenAI and Gemini adapters. Start with `jina-embeddings-v5-text-small` (1024 dimensions, `retrieval.passage` for chunks, `retrieval.query` for questions); set provider/model/dimension/task mode by configuration. Use the same embedding space for source and query vectors; reject dimension mismatch and plan re-indexing when it changes.
- [ ] Persist messages, validated citations and answer/refusal state. Honor the accepted durable idempotency ledger: one committed user/assistant pair per key, replay same payload, `409` for conflicting or in-progress reuse.
- [ ] Build the React lesson-viewer entry point, chat/history, readiness state and citation navigation against Spring's public API.

### F5. Functional guardrails and focused tests

- [ ] Enforce Student-only access to eligible `PUBLISHED` courses with `ACTIVE` enrollment; deny `COMPLETED` and block an `UPCOMING` course until learning content opens. Block AI during an `IN_PROGRESS` quiz **in the same course**, including chat history, using authoritative assessment state. Use only eligible `AVAILABLE` content from lessons currently unlocked for that student; M2's `SEQUENTIAL` rule must not be bypassed. There is no per-lesson AI toggle. Fail closed when required checks are unavailable.
- [ ] Implement the agreed completed JSON response for the first chat release, keeping the provider interface capable of later token events. Record SSE as a later functional enhancement and preserve idempotency when it is added.
- [ ] Add focused tests for each parser, duplicate/stale/delete jobs, course isolation, owner isolation, access revocation, idempotency, generation/embedding provider switching, abstention, citation validation and Java → Python contract. Run the same vector-store contract tests against pgvector and Pinecone adapters; verify no unsupported source can produce an answer.

### F6. Functional review/demo

- [ ] Demonstrate an authorized student asking from all four supported formats, correct citations, insufficient-evidence refusal, unavailable-index state, foreign-course/foreign-user denial and content deletion with no stale answer.
- [ ] Update the M7 design, OpenAPI and backend integration docs with the implemented behavior and mark functional checklist items only after verification.

### F7. Later functional enhancement

- [ ] Add SSE streaming as the later functional enhancement from the original checklist. Specify event types, final validated citations, terminal errors, disconnects and retry/replay rules in both public and private contracts before implementation.

## Phase N — Non-functional requirements later

These targets are retained for a separate milestone; none is claimed met by the functional demo. Basic error handling and correctness tests in Phase F are still required.

### N1. Performance and scale

- [ ] Build the repeatable ≥20-page PDF/10-question retrieval PoC, then the 80-question evaluation set (40 in scope, 15 out of scope, 10 injection, 15 cross-course). Compare chunk size, top-k and threshold; target top-5 evidence ≥8/10, Recall@5 ≥0.85, MRR ≥0.70, groundedness ≥90% and out-of-scope refusal ≥95%.
- [ ] Benchmark retrieval p95 ≤150 ms at 20,000 chunks; first-token p95 ≤3 s and full-answer p95 ≤10 s where streaming applies. Measure a 50-page document at ≤3 minutes, batches of 32 embeddings, at most 3 parallel jobs, and 20 simultaneous chat clients.
- [ ] Tune query threshold (initial checklist value 0.75), vector search, chunking and rate limits using measured Vietnamese/English course data. Treat numerical thresholds as targets requiring evidence.

### N2. Reliability, operations and security hardening

- [ ] Define total request deadline and provider retry budget; the old 20-second LLM timeout conflicts with a 10-second full-answer p95 target. For SSE, retry only before the first event and send a terminal error afterward.
- [ ] Add bounded backoff (up to three embedding retries), dead-letter handling, health/readiness, observability, secret management, data-handling controls and deployment/CI checks.
- [ ] Load-test and enforce the proposed 30 questions/user/hour with 429; complete a 10-case prompt-injection evaluation and 100 cross-course query audit with zero leakage.

### N3. Cost, retention and provider migration

- [ ] Measure p50/p95 latency, token use, cost per 1,000 questions and indexing cost for 100 documents. Forecast 100/500/1,000 students and demonstrate ≥20% input-token or per-question cost reduction from a measured baseline.
- [ ] Agree on retention/deletion and backup-purge periods; implement scheduled purge, spend alerts and provider/model migration plus re-embedding procedures.

### N4. P5 AI Engineering report

- [ ] Produce the four required report sections, at least three measured evidence tables/logs, and team review by two members; keep the report within 15 pages.

## Engineering contracts to finalize before cross-service integration

1. Specify M2 indexing event delivery and authoritative content/version reads in executable OpenAPI and tests. Connect the implemented Java issuer and Python verifier through authorized private routes and contract tests. Prefer the existing backend outbox/RabbitMQ pattern; keep the agreed synchronous deactivation path separate.
2. Assign AI table migration ownership exclusively to M7 and reconcile legacy Java AI entities/baseline tables before either service writes them.
3. Locate the authoritative same-course quiz checker in the live backend. Add the `FILE` MIME rules and Admin-facing index status integration in M2; verify the actual M2 course/content visibility semantics.
4. Verify Jina's account/model terms for any production or commercial use. The functional implementation may start with the configured research/prototype account.
