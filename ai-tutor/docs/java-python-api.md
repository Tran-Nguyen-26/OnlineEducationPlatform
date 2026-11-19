# M7 private Java Spring Boot → Python FastAPI contract (draft)

**Status:** Functional scope and public path agreed; private integration details remain a draft and endpoints are not yet implemented. Public requests go to Spring Boot at `/api/courses/{courseId}/ai/...`, matching the live gateway's `/api/courses/**` pattern; the browser never calls these private paths. Shared public request/response schemas are in [`m7-ai-tutor.yaml`](../openapi/m7-ai-tutor.yaml). The first release returns a completed JSON chat response, with SSE retained as a later functional enhancement. Finalize service identity before implementing the Java client and FastAPI handlers.

## Responsibilities

1. Spring Boot authenticates the end-user JWT and checks Student role, `PUBLISHED` course, `ACTIVE` enrollment and no `IN_PROGRESS` quiz in that course on **every** M7 request, including status, history and replay. Retrieval uses only current `AVAILABLE` learning content of the authorized course and supported formats; there is no per-lesson AI switch. Instructors and Admin receive no M7 access. Spring derives `user_id` from the authenticated principal. If an authoritative check is unavailable, fail closed with 503.
2. Spring Boot sends a private request to M7 with a short-lived signed service assertion carrying issuer, audience `m7-ai-tutor`, verified subject, course ID, issue/expiry time, request ID and a unique token ID. The assertion design (signature/key rotation and optional mTLS) is pending ADR-0009; never accept a browser-supplied user/course header as identity.
3. FastAPI validates the assertion, compares its course claim with the path and enforces conversation owner/course on reads, writes and deletes. A foreign conversation ID returns 404. FastAPI owns RAG, persistence and provider adapters; it must not read the public end-user JWT as its only service credential.
4. Spring Boot forwards public request IDs and `Idempotency-Key` to M7, maps M7 errors to the public envelope, and does not silently retry a non-idempotent send with a new key. Java and Python must share contract tests for success and error cases.

## Proposed private routes

All routes below use the private service origin, require the signed service assertion, and are never added to a public gateway route. Request bodies, pagination parameters and successful JSON response shapes match the corresponding public operation in the OpenAPI unless explicitly noted.

| Spring public method/path suffix | FastAPI private method/path | OpenAPI operation ID |
| --- | --- | --- |
| `GET /courses/{courseId}/ai/status` | `GET /internal/v1/ai/courses/{courseId}/status` | `getAiTutorStatus` |
| `POST /courses/{courseId}/ai/conversations` | `POST /internal/v1/ai/courses/{courseId}/conversations` | `createAiConversation` |
| `GET /courses/{courseId}/ai/conversations` | `GET /internal/v1/ai/courses/{courseId}/conversations` | `listMyAiConversations` |
| `GET /courses/{courseId}/ai/conversations/{conversationId}` | `GET /internal/v1/ai/courses/{courseId}/conversations/{conversationId}` | `getMyAiConversation` |
| `DELETE /courses/{courseId}/ai/conversations/{conversationId}` | `DELETE /internal/v1/ai/courses/{courseId}/conversations/{conversationId}` | `deleteMyAiConversation` |
| `GET /courses/{courseId}/ai/conversations/{conversationId}/messages` | `GET /internal/v1/ai/courses/{courseId}/conversations/{conversationId}/messages` | `listMyAiMessages` |
| `POST /courses/{courseId}/ai/conversations/{conversationId}/messages` | `POST /internal/v1/ai/courses/{courseId}/conversations/{conversationId}/messages` | `sendAiTutorMessage` |

The existing private indexing operations in the OpenAPI remain `POST /internal/v1/ai/index-jobs`, `GET /internal/v1/ai/index-jobs/{jobId}`, and `POST /internal/v1/ai/sources/{contentId}/deactivate`. These use an M2 service identity with the minimum scope required; they are separate from a Student chat assertion. Invalidation is synchronous: M2 makes the source unavailable, M7 excludes it from answers and acknowledges, then M2 may finalize deletion/replacement.

## Error and transport contract to finish

- The first release returns one JSON `ChatTurnResponse`. The original checklist's SSE remains a later functional enhancement. Before adding it, define event types, a final citation/usage event, terminal errors, disconnect behavior and `Idempotency-Key` replay on both sides.
- Forward `AI_INDEX_NOT_READY` (409), `AI_CONVERSATION_NOT_FOUND` (404), `IDEMPOTENCY_CONFLICT` / `AI_REQUEST_IN_PROGRESS` (409), and provider unavailability (503) without exposing provider credentials or internal traces. Authorization denial happens in Spring before any M7 retrieval/LLM call.
- Every call includes `X-Request-Id`; send-message includes `Idempotency-Key`. Exact internal JWT claims, TTL and key rotation, service timeout and retry mapping are the remaining contract decisions.
