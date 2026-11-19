# M7 private Java Spring Boot → Python FastAPI contract (draft)

**Status:** Functional scope and public path agreed. The student service-assertion verifier in Python and issuer in core LMS are implemented and independently tested; Java authorization/proxy routes, private chat/index handlers and exact error mapping remain to implement. Public requests go to Spring Boot at `/api/courses/{courseId}/ai/...`, matching the live gateway's `/api/courses/**` pattern; the browser never calls these private paths. Shared public request/response schemas are in [`m7-ai-tutor.yaml`](../openapi/m7-ai-tutor.yaml). The first release returns a completed JSON chat response, with SSE retained as a later functional enhancement.

## Responsibilities

1. Spring Boot authenticates the end-user JWT and checks Student role, `PUBLISHED` course, `ACTIVE` enrollment, non-`UPCOMING` course phase and no `IN_PROGRESS` quiz in that course on **every** M7 request, including status, history and replay. Retrieval uses only current `AVAILABLE` learning content of supported formats in lessons currently unlocked for that Student; there is no per-lesson AI switch. Instructors and Admin receive no M7 access. Spring derives `user_id` from the authenticated principal. If an authoritative check is unavailable, fail closed with 503.
2. The core LMS Spring Boot service sends a private request to M7 with a dedicated HS256 service assertion after its live authorization checks. Its secret is distinct from the public end-user JWT key and is supplied to both services as `AI_TUTOR_SERVICE_JWT_SECRET` (at least 32 UTF-8 bytes). Never accept a browser-supplied user/course header as identity.
3. FastAPI validates the assertion, compares its course claim with the path and enforces conversation owner/course on reads, writes and deletes. A foreign conversation ID returns 404. FastAPI owns RAG, persistence and provider adapters; it must not read the public end-user JWT as its only service credential.
4. Spring Boot forwards public request IDs and `Idempotency-Key` to M7, maps M7 errors to the public envelope, and does not silently retry a non-idempotent send with a new key. Java and Python must share contract tests for success and error cases.

## Student assertion contract

- `Authorization: Bearer <internal-jwt>` and `X-Request-Id: <UUID>` are required on every private student route. The token uses HS256 with `iss=oep-core-lms`, `aud=m7-ai-tutor`, `scope=ai:student`, and required `sub` (verified Student UUID), `course_id` (verified course UUID), `request_id` (same UUID as the header), `jti` (unique UUID), and integer-second `iat`, `nbf`, `exp` claims.
- The token lifetime is positive and at most 60 seconds; `nbf` cannot precede `iat`. FastAPI accepts at most three seconds of clock skew and requires the token course to match `{courseId}`. A missing, malformed, expired, incorrectly signed or mismatched assertion returns 401 before business logic. FastAPI does not accept the public user JWT as a substitute.
- The Java issuer can mint a fresh assertion, but its caller must first check Student role, `PUBLISHED` course, `ACTIVE` enrollment, non-`UPCOMING` phase and same-course quiz state for that request. Both signing and verification are implemented; authorization/proxy wiring, deployment secret provisioning, rotation procedure and indexing-service scope are pending. No private route is publicly exposed.
- The live backend has `SEQUENTIAL` lesson locks. Before connecting private chat, define how Spring conveys the authoritative per-student readable-lesson set to Python on each request so vector queries exclude locked lessons. The current student assertion binds user/course/request but does not yet encode lesson scope; do not treat course access alone as permission to use every lesson.

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
- Spring returns `QUIZ_LOCKED` (409) before proxying when an attempt is `IN_PROGRESS` in the same course. Forward `AI_INDEX_NOT_READY` (409), `AI_CONVERSATION_NOT_FOUND` (404), `IDEMPOTENCY_CONFLICT` / `AI_REQUEST_IN_PROGRESS` (409), and provider unavailability (503) without exposing provider credentials or internal traces. Authorization denial happens in Spring before any M7 retrieval/LLM call.
- Every call includes `X-Request-Id`; send-message includes `Idempotency-Key`. Indexing-service assertion scope, service timeout and retry mapping remain to be specified with their handlers. A coordinated secret-rotation procedure is needed before deployment.
