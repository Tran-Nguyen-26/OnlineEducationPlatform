# M7 AI Tutor service

Python/FastAPI is a private service called by the Spring Boot backend. It verifies short-lived internal student assertions, owns versioned relational migrations and serves private status and conversation/history routes. It also has standalone parsers for the four agreed content formats. Spring has authorized public proxy routes. Send-turn, indexing and retrieval handlers are still pending; track them in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

`oep_ai_tutor.embeddings` provides Jina, OpenAI and Gemini adapters behind a shared `EmbeddingProvider` interface. Jina `jina-embeddings-v5-text-small` at 1024 dimensions is the default; set `AI_TUTOR_EMBEDDING_PROVIDER` (`jina`, `openai`, `gemini`), `AI_TUTOR_EMBEDDING_MODEL`, `AI_TUTOR_EMBEDDING_DIMENSION` and the selected provider's `JINA_API_KEY`, `OPENAI_API_KEY` or `GEMINI_API_KEY`. Pass an `httpx.Client` with a bounded timeout to `embedding_provider_from_env`. The adapters reject missing, non-finite or wrong-dimension vectors and use passage/document versus query task modes. Keep `AI_TUTOR_EMBEDDING_SPACE_KEY` aligned with the selected provider, model, dimension and task settings; changing any of these requires re-indexing. The adapters are tested with mocked provider responses but are not yet called by the indexing or retrieval pipeline.

`oep_ai_tutor.generation` provides Groq, OpenAI and Gemini completed-response adapters. The default is Groq `openai/gpt-oss-120b`; choose an adapter with `AI_TUTOR_GENERATION_PROVIDER` (`groq`, `openai`, `gemini`), set `AI_TUTOR_GENERATION_MODEL` for alternate providers, `AI_TUTOR_MAX_OUTPUT_TOKENS` for the output cap, and the selected `GROQ_API_KEY`, `OPENAI_API_KEY` or `GEMINI_API_KEY`. Pass a bounded-timeout `httpx.Client` to `generation_provider_from_env`. The caller supplies trusted instructions separately from user/assistant messages; retrieved content must be passed as untrusted message content. Adapters reject truncated, empty or malformed responses and normalize input/output token counts. They are not yet connected to send-turn or citation validation.

`oep_ai_tutor.chat_requests` supplies durable idempotency claims and atomic persistence of citation-free `ABSTAINED` turns. Claims are tied to the signed student's conversation, key and normalized message/readable-lesson scope. Completed requests replay their saved assistant-message ID; in-progress requests conflict, failed retryable requests can resume, and a stale attempt cannot commit after a replacement claim. Call these functions with an autocommit PostgreSQL connection so each claim and completion is independently durable. Authorization must be rechecked before every claim or replay. The private send route and grounded `ANSWERED` turn with server-validated citation snapshots remain to be connected.

`oep_ai_tutor.index_jobs` records M2-shaped versioned UPSERT/DEACTIVATE events in `ai_index_jobs`. Its `event_id` is a durable deduplication key: exact redelivery returns the saved job; a different payload with the same ID conflicts. `superseded_event` detects an older version or an UPSERT revoked at the same version. A future worker must recheck that condition in its final source-state transaction. Private `POST /internal/v1/ai/index-jobs` and `GET /internal/v1/ai/index-jobs/{jobId}` require a short-lived `ai:index` assertion issued by Spring and reject Student assertions. M2 event delivery, worker and synchronous deletion acknowledgement remain to be wired.

`oep_ai_tutor.parsing` converts inline `TEXT`, text PDF, Markdown `.md` and JSON `.json` into citation-aware passages and bounded chunks. PDF page, Markdown heading and JSON path remain attached to each chunk. Empty or image-only PDFs report `OCR_REQUIRED`; encrypted PDFs and unsupported file types are rejected. M2 now validates AI-eligible Admin uploads (20 MiB PDF; 5 MiB Markdown/JSON) against the stored object's size and MIME type, and the Admin UI checks size before upload. Parsing is not yet connected to M7 indexing; Admin-visible processing state remains open.

M7 owns its PostgreSQL tables and migrations in the `ai_tutor` schema. The historical `public.ai_*` tables from the Java baseline are unused; see [ADR 0015](adr/0015-ai-relational-ownership.md). The migration is implemented, but application database writes are not yet connected.

## Database migrations

Provision a dedicated PostgreSQL database role for M7 with permission to create/use the `ai_tutor` schema and no write permission on LMS-owned `public` tables. Set `AI_TUTOR_DATABASE_URL` to that role's PostgreSQL URL, then run:

```powershell
uv run --locked python -m oep_ai_tutor.migrate
```

The runner applies numbered SQL files transactionally under an advisory lock, records SHA-256 checksums in `ai_tutor.schema_migrations`, and rejects edits to an already applied migration. Re-running it with no pending migration is safe. V1 creates relational metadata and constraints; V2 requires a PostgreSQL installation with the pgvector extension available and adds native vector storage, dimension validation and an initial 1024-dimensional HNSW index. The database role applying V2 must be allowed to create the extension, or an administrator must install it first. These migrations do not alter Java Flyway history or the historical `public.ai_*` tables. Set `AI_TUTOR_DATABASE_URL` and run migrations before using the private conversation routes. Without the URL, those routes return 503.

`oep_ai_tutor.pgvector_store.PgvectorStore` writes vectors to existing M7 chunk rows and performs exact search after filtering by verified course, readable lessons, active embedding space, current `READY` revision and dimension. Deactivation hides a revision and clears its vectors in one PostgreSQL transaction. The HNSW index is provisioned for later measured approximate search; current exact search materializes eligible rows before ranking so SQL scope filters cannot be dropped by an ANN candidate scan.

`oep_ai_tutor.vector_store.vector_store_from_env` selects `VECTOR_STORE=pgvector` (default) or `VECTOR_STORE=pinecone`. Pinecone requires `PINECONE_INDEX_HOST` (the HTTPS origin of a dense cosine index with the configured dimension), `PINECONE_API_KEY`, optional `PINECONE_NAMESPACE` (default `ai-tutor`), and `AI_TUTOR_EMBEDDING_DIMENSION` (default 1024). Provision a separate compatible index when the embedding space or dimension changes. Pinecone queries include course, readable lessons, space, dimension and current revision IDs as metadata filters, then verify returned chunk IDs against current PostgreSQL source state. Deactivation excludes the revision in PostgreSQL before requesting Pinecone deletion; a failed remote delete remains retryable and cannot make the old revision answerable. Both adapters pass the same isolation/version/deactivation tests with a Pinecone HTTP fake and disposable pgvector PostgreSQL. Live Pinecone validation and ingestion/retrieval wiring remain open.

Set `AI_TUTOR_EMBEDDING_SPACE_KEY` to the active provider/model/dimension space before using the status route. Status counts only the latest revision of each content in that space and the asserted course. Missing database or space configuration returns 503. The key must match the one later used by indexing and retrieval; switching embedding spaces requires re-indexing.

## Local setup

Requires Python 3.11+ and `uv`.

```powershell
uv sync --locked
$env:AI_TUTOR_SERVICE_JWT_SECRET = '<a unique secret of at least 32 UTF-8 bytes>'
uv run --locked uvicorn oep_ai_tutor.app:create_app --factory --host 127.0.0.1 --port 8000
```

`GET /healthz` returns `{"status":"ok"}`. The service refuses to start when `AI_TUTOR_SERVICE_JWT_SECRET` is absent or shorter than 32 bytes. Core LMS now has conditional public proxy routes configured with `AI_TUTOR_ENABLED=true`, the same dedicated secret and `AI_TUTOR_BASE_URL` pointing to the private FastAPI origin (for example, `http://127.0.0.1:8000` outside Docker). Private status and conversation/history handlers work; send-turn and indexing do not yet exist, so enabling the proxy does not make chat functional. Do not reuse the public end-user JWT signing key. Keep the private service off public gateway routes.

## Tests

```powershell
uv run --locked pytest -q
```

See [the Java/Python contract](docs/java-python-api.md) for assertion claims and private paths. The test suite exercises the FastAPI authentication dependency, conversation ownership, SQL constraints, parsing and mocked provider HTTP contracts. No unimplemented send-turn route is exposed in the service.

The PostgreSQL migration integration test runs when `AI_TUTOR_TEST_DATABASE_URL` points to an **isolated, disposable** PostgreSQL database with pgvector installed (for example, the `pgvector/pgvector:0.8.7-pg17` Docker image). It verifies fresh/repeat migration, checksum drift, dimension rejection and the database constraints. Without that variable it is skipped by the normal `pytest` command.
