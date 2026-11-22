# M7 AI Tutor service

Python/FastAPI is a private service called by the Spring Boot backend. It verifies short-lived internal student assertions, owns versioned relational migrations, serves private status, conversation/history/send and index-job routes, and runs a separate worker for the four agreed content formats. Spring has authorized public proxy routes, and the React lesson viewer now has an AI Tutor panel. Track remaining integration and functional checks in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

`oep_ai_tutor.embeddings` provides Jina, OpenAI and Gemini adapters behind a shared `EmbeddingProvider` interface. Jina `jina-embeddings-v5-text-small` at 1024 dimensions is the default; set `AI_TUTOR_EMBEDDING_PROVIDER` (`jina`, `openai`, `gemini`), `AI_TUTOR_EMBEDDING_MODEL`, `AI_TUTOR_EMBEDDING_DIMENSION` and the selected provider's `JINA_API_KEY`, `OPENAI_API_KEY` or `GEMINI_API_KEY`. Pass an `httpx.Client` with a bounded timeout to `embedding_provider_from_env`. The adapters reject missing, non-finite or wrong-dimension vectors and use passage/document versus query task modes. Keep `AI_TUTOR_EMBEDDING_SPACE_KEY` aligned with the selected provider, model, dimension and task settings; changing any of these requires re-indexing. The worker embeds passages and the send route embeds questions in the same configured space.

`oep_ai_tutor.generation` provides Groq, OpenAI and Gemini completed-response adapters. The default is Groq `openai/gpt-oss-120b`; choose an adapter with `AI_TUTOR_GENERATION_PROVIDER` (`groq`, `openai`, `gemini`), set `AI_TUTOR_GENERATION_MODEL` for alternate providers, `AI_TUTOR_MAX_OUTPUT_TOKENS` for the output cap, and the selected `GROQ_API_KEY`, `OPENAI_API_KEY` or `GEMINI_API_KEY`. Pass a bounded-timeout `httpx.Client` to `generation_provider_from_env`. The send route supplies trusted instructions separately from untrusted retrieved excerpts, accepts only model answers citing numbered evidence, and rebuilds citations from current locked M7 rows. Adapters reject truncated, empty or malformed responses and normalize input/output token counts.

`oep_ai_tutor.chat_requests` supplies durable idempotency claims and atomic persistence of `ANSWERED` or `ABSTAINED` turns. Claims are tied to the signed student's conversation, key and normalized message/readable-lesson scope. Completed requests replay their saved assistant-message ID; in-progress requests conflict, failed retryable requests can resume, and a stale attempt cannot commit after a replacement claim. The send route uses an autocommit PostgreSQL connection so each claim and completion is independently durable. It rechecks the signed lesson scope, course and owner before every claim or replay; grounded citations are validated against current M7 rows and M2 source eligibility before they are returned.

`oep_ai_tutor.index_jobs` records M2-shaped versioned UPSERT/DEACTIVATE events in `ai_index_jobs`. Its `event_id` is a durable deduplication key: exact redelivery returns the saved job; a different payload with the same ID conflicts. `superseded_event` detects an older version or an UPSERT revoked at the same version. The polling worker rechecks that condition before activating vectors. Private `POST /internal/v1/ai/index-jobs` and `GET /internal/v1/ai/index-jobs/{jobId}` require a short-lived `ai:index` assertion issued by Spring and reject Student assertions. M2 outbox delivery through RabbitMQ is implemented.

`oep_ai_tutor.source_client` requests the exact source version for an UPSERT job from M2's private `GET /internal/v1/ai/sources/{contentId}` route. Configure the private core LMS origin for the worker as `AI_TUTOR_M2_BASE_URL`; the client signs `ai:source:read` assertions with `AI_TUTOR_SERVICE_JWT_SECRET` and validates M2's response against the job. M2 returns eligible inline TEXT or file metadata and a short-lived download grant, never a raw object key. The executable contract is [m7-m2-source.yaml](openapi/m7-m2-source.yaml).

`POST /internal/v1/ai/sources/{contentId}/deactivate` requires the same index assertion and a body with `course_id`, `lesson_id`, and `version_number`. It stores a deterministic deactivation event, marks matching revisions inactive across embedding spaces, and clears pgvector evidence before returning 204. It then asks the configured store to clean up each revision; failure returns 503 so M2 can retry. A tombstone before a delayed UPSERT prevents that version from being accepted by the future worker. M2 now commits `DELETING` before calling this route and finalizes deletion after 204.

When M7 is enabled, Admin edits to AI-eligible inline TEXT or citation metadata commit `INVALIDATING` before the same deactivation call. M2 restores `AVAILABLE`, increments the version, writes history and enqueues a new UPSERT in one transaction only after M7 acknowledges. A failed call leaves the source unavailable and the PATCH can be retried. Eligible file replacement uses the same handshake before issuing a new upload grant. Completion verifies the new stored object, then commits the incremented version and UPSERT together. First file completion keeps version 1 for both the initial event and history.

`oep_ai_tutor.parsing` converts inline `TEXT`, text PDF, Markdown `.md` and JSON `.json` into citation-aware passages and bounded chunks. PDF page, Markdown heading and JSON path remain attached to each chunk. Empty or image-only PDFs report `OCR_REQUIRED`; encrypted PDFs and unsupported file types are rejected. M2 now validates AI-eligible Admin uploads (20 MiB PDF; 5 MiB Markdown/JSON) against the stored object's size and MIME type, and the Admin UI checks size before upload. Parsing is connected to M7 indexing; Admin-visible processing state remains open.

M7 owns its PostgreSQL tables and migrations in the `ai_tutor` schema. The historical `public.ai_*` tables from the Java baseline are unused; see [ADR 0015](adr/0015-ai-relational-ownership.md). Conversation, idempotency, index-job and source-chunk writes are connected.

## Database migrations

Provision a dedicated PostgreSQL database role for M7 with permission to create/use the `ai_tutor` schema and no write permission on LMS-owned `public` tables. Set `AI_TUTOR_DATABASE_URL` to that role's PostgreSQL URL, then run:

```powershell
uv run --locked python -m oep_ai_tutor.migrate
```

The runner applies numbered SQL files transactionally under an advisory lock, records SHA-256 checksums in `ai_tutor.schema_migrations`, and rejects edits to an already applied migration. Re-running it with no pending migration is safe. V1 creates relational metadata and constraints; V2 requires a PostgreSQL installation with the pgvector extension available and adds native vector storage, dimension validation and an initial 1024-dimensional HNSW index. V3 stores M2's versioned course-publication access state. The database role applying V2 must be allowed to create the extension, or an administrator must install it first. These migrations do not alter Java Flyway history or the historical `public.ai_*` tables. Set `AI_TUTOR_DATABASE_URL` and run migrations before using the private routes. Without the URL, those routes return 503.

`oep_ai_tutor.pgvector_store.PgvectorStore` writes vectors to existing M7 chunk rows and performs exact search after filtering by verified course, readable lessons, active embedding space, current `READY` revision and dimension. Deactivation hides a revision and clears its vectors in one PostgreSQL transaction. The HNSW index is provisioned for later measured approximate search; current exact search materializes eligible rows before ranking so SQL scope filters cannot be dropped by an ANN candidate scan.

## Index event consumer

Start the RabbitMQ consumer as a separate M7 process after migrations. Use the same `AI_TUTOR_DATABASE_URL` as the API and set `AI_TUTOR_RABBITMQ_URL` to the private broker URL. For the local Docker Compose broker exposed on port 5673, the URL is `amqp://oep:oep@127.0.0.1:5673/%2F`. Run `uv run --locked python -m oep_ai_tutor.index_consumer`. The consumer and Java worker both declare the durable `oep.ai-tutor.index` queue and its dead letter queue, so start at least one of them before the M2 outbox relay publishes events. Grant the M7 broker account access only to the AI queue/exchange where broker permissions permit.

The consumer accepts only the six versioned event fields in `application/json` messages with a matching RabbitMQ message ID. It acknowledges after the M7 job transaction commits. Duplicate delivery reuses the existing job, malformed/conflicting payloads go to `oep.ai-tutor.index.dlq`, and database/broker errors close the connection so the unacknowledged delivery is retried after reconnect. Investigate and replay dead letter events after correcting their cause. This process records jobs; run the indexing worker separately.

## Indexing worker

Set `AI_TUTOR_M2_BASE_URL` to the private core LMS origin, `AI_TUTOR_STORAGE_ORIGIN` to the exact scheme and host of its presigned file downloads, and `AI_TUTOR_EMBEDDING_SPACE_KEY` to the active embedding configuration. Also set `AI_TUTOR_DATABASE_URL`, `AI_TUTOR_SERVICE_JWT_SECRET`, embedding provider credentials and the vector-store settings described above. Start a separate process after migrations and the event consumer:

```powershell
uv run --locked python -m oep_ai_tutor.index_worker
```

The worker claims durable jobs, reclaims a stalled job after 15 minutes, and retries transient failures at most three times. It fetches a version-bound M2 snapshot, streams files without following redirects, enforces the declared size and format cap, and parses TEXT, text PDF, Markdown or JSON. It persists source, lesson, version and page/heading/JSON-path provenance with embeddings. It checks M2 again before setting a revision `READY`; a removed or superseded source remains excluded from vector search. Scanned PDFs become `UNSUPPORTED` with `OCR_REQUIRED`. Integration tests use a disposable pgvector database and fake M2/storage/provider responses. Live provider credentials and an Admin processing-status route remain pending.

When an Admin changes course lifecycle status, core LMS first commits its authoritative status, then calls the private versioned `PUT /internal/v1/ai/courses/{courseId}/access` route with an `ai:index` assertion. `active=false` blocks M7 student routes and excludes the course from both vector adapters and grounded citation validation. A failed acknowledgement leaves the M2 course status committed; retry the same Admin status request to resend the same or newer version. Old delivery cannot reopen a course. Workers keep queued jobs while the course is inactive and resume when M2 republishes it; unchanged current source revisions remain usable after reactivation, while modified content is re-indexed through M2's publication outbox.

When AI Tutor is enabled, Admins can inspect source processing state under the course's **Content** tab. The browser calls only M2's `GET /api/admin/courses/{courseId}/ai/index-status`; M2 checks the existing Admin security rule and forwards with an `ai:index` assertion to M7's private metadata-only `GET /internal/v1/ai/courses/{courseId}/index-status`. The list includes M7-known jobs and revisions. The **Đồng bộ lại tài liệu** button calls M2's `POST /api/admin/courses/{courseId}/ai/reconcile`: M2 compares current eligible source versions with M7's status and writes recovery events to its outbox for missing/older versions. Repeated recovery requests use a stable event ID, so M7 intake deduplicates them. Failed and unsupported sources remain visible for investigation; this operation does not force them to reindex.

`oep_ai_tutor.vector_store.vector_store_from_env` selects `VECTOR_STORE=pgvector` (default) or `VECTOR_STORE=pinecone`. Pinecone requires `PINECONE_INDEX_HOST` (the HTTPS origin of a dense cosine index with the configured dimension), `PINECONE_API_KEY`, optional `PINECONE_NAMESPACE` (default `ai-tutor`), and `AI_TUTOR_EMBEDDING_DIMENSION` (default 1024). Provision a separate compatible index when the embedding space or dimension changes. Pinecone queries include course, readable lessons, space, dimension and current revision IDs as metadata filters, then verify returned chunk IDs against current PostgreSQL source state. Retrieval independently revalidates every match against current PostgreSQL rows and newer jobs. Deactivation excludes the revision in PostgreSQL before requesting Pinecone deletion; a failed remote delete remains retryable and cannot make the old revision answerable. Both adapters pass the same isolation/version/deactivation tests with a Pinecone HTTP fake and disposable pgvector PostgreSQL. Live Pinecone validation remains open.

Set `AI_TUTOR_EMBEDDING_SPACE_KEY` to the active provider/model/dimension space before using the status route. Status counts latest revisions and queued/superseding jobs in that space and the asserted course. Missing database or space configuration returns 503. The key must match the one used by indexing and retrieval; switching embedding spaces requires re-indexing.

## Local setup

Requires Python 3.11+, `uv`, the Java backend and frontend. For a local pgvector database, run `docker compose --profile ai-tutor up -d ai-postgres` from `backend/`; the optional service uses port 5435 and a separate dev-only volume. Copy [.env.example](.env.example) to an ignored `.env.local`, fill a unique shared service secret and the selected embedding/generation API keys, and dot-source `scripts/load-local-env.ps1` in each M7 PowerShell terminal. The default local choice is Jina embeddings plus Groq generation. Set the **same** `AI_TUTOR_SERVICE_JWT_SECRET` in core LMS, together with `AI_TUTOR_ENABLED=true` and `AI_TUTOR_BASE_URL=http://127.0.0.1:8000`. Start core LMS, its gateway/worker, RabbitMQ and MinIO using the [backend quick start](../backend/README.md); keep `AI_TUTOR_M2_BASE_URL` pointed at core LMS port 8081. Start the frontend with `npm run dev` from `frontend/` and open a Student course viewer, then select **AI Tutor**.

In `ai-tutor/`, run `. .\scripts\load-local-env.ps1` in each terminal, then start these M7 processes (the last three keep running in separate terminals):

```powershell
uv run --locked python -m oep_ai_tutor.migrate
uv run --locked python -m oep_ai_tutor.index_consumer
uv run --locked python -m oep_ai_tutor.index_worker
uv run --locked uvicorn oep_ai_tutor.app:create_app --factory --host 127.0.0.1 --port 8000
```

For the core LMS terminal, load the same `.env.local` file with `. ..\ai-tutor\scripts\load-local-env.ps1`, set `$env:AI_TUTOR_ENABLED='true'` and `$env:AI_TUTOR_BASE_URL='http://127.0.0.1:8000'`, then start core LMS from `backend/`. This keeps the shared secret identical without writing it to tracked backend configuration.

Run the consumer, worker and API in separate terminals. `GET /healthz` returns `{"status":"ok"}`. The service refuses to start when `AI_TUTOR_SERVICE_JWT_SECRET` is absent or shorter than 32 bytes. Do not reuse the public end-user JWT signing key. Keep the private service off public gateway routes. A course must be `PUBLISHED`, the Student must have active access, eligible content must finish indexing, and no same-course quiz may be in progress. The first response is completed JSON; the UI shows the cited source and opens a fresh protected M2 file URL when selected. Live provider and Java-to-Python process validation still require configured credentials.

## Tests

```powershell
uv run --locked pytest -q
```

See [the Java/Python contract](docs/java-python-api.md) for assertion claims and private paths. The test suite exercises FastAPI authentication, signed lesson scope, conversation ownership, indexing, scoped retrieval, grounded send/replay/refusal, SQL constraints, parsing and mocked provider HTTP contracts.

The PostgreSQL migration integration test runs when `AI_TUTOR_TEST_DATABASE_URL` points to a **fresh, isolated, disposable** PostgreSQL database with pgvector installed (for example, the `pgvector/pgvector:0.8.7-pg17` Docker image). It verifies fresh/repeat migration, checksum drift, dimension rejection and the database constraints. Set `AI_TUTOR_TEST_RABBITMQ_URL` to a disposable broker URL as well to exercise the real queue, duplicate receipt and dead letter flow. Without those variables the corresponding integration tests are skipped.
