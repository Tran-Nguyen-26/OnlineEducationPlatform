# M7 AI Tutor service

Python/FastAPI is a private service called by the Spring Boot backend. It verifies short-lived internal student assertions, owns versioned relational migrations and serves private status and conversation/history routes. It also has standalone parsers for the four agreed content formats. Spring has authorized public proxy routes. Send-turn, indexing and retrieval handlers are still pending; track them in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

`oep_ai_tutor.parsing` converts inline `TEXT`, text PDF, Markdown `.md` and JSON `.json` into citation-aware passages and bounded chunks. PDF page, Markdown heading and JSON path remain attached to each chunk. Empty or image-only PDFs report `OCR_REQUIRED`; encrypted PDFs and unsupported file types are rejected. M2 now validates AI-eligible Admin uploads (20 MiB PDF; 5 MiB Markdown/JSON) against the stored object's size and MIME type, and the Admin UI checks size before upload. Parsing is not yet connected to M7 indexing; Admin-visible processing state remains open.

M7 owns its PostgreSQL tables and migrations in the `ai_tutor` schema. The historical `public.ai_*` tables from the Java baseline are unused; see [ADR 0015](adr/0015-ai-relational-ownership.md). The migration is implemented, but application database writes are not yet connected.

## Database migrations

Provision a dedicated PostgreSQL database role for M7 with permission to create/use the `ai_tutor` schema and no write permission on LMS-owned `public` tables. Set `AI_TUTOR_DATABASE_URL` to that role's PostgreSQL URL, then run:

```powershell
uv run --locked python -m oep_ai_tutor.migrate
```

The runner applies numbered SQL files transactionally under an advisory lock, records SHA-256 checksums in `ai_tutor.schema_migrations`, and rejects edits to an already applied migration. Re-running it with no pending migration is safe. The initial migration creates relational metadata and constraints; pgvector storage and the vector adapters are later work. It does not alter Java Flyway history or the historical `public.ai_*` tables. Set `AI_TUTOR_DATABASE_URL` and run migrations before using the private conversation routes. Without the URL, those routes return 503.

Set `AI_TUTOR_EMBEDDING_SPACE_KEY` to the active provider/model/dimension space before using the status route. Status counts only the latest revision of each content in that space and the asserted course. Missing database or space configuration returns 503. The key must match the one later used by indexing and retrieval; switching embedding spaces requires re-indexing.

## Local setup

Requires Python 3.11+ and `uv`.

```powershell
uv sync --locked
$env:AI_TUTOR_SERVICE_JWT_SECRET = '<a unique secret of at least 32 UTF-8 bytes>'
uv run --locked uvicorn oep_ai_tutor.app:create_app --factory --host 127.0.0.1 --port 8000
```

`GET /healthz` returns `{"status":"ok"}`. The service refuses to start when `AI_TUTOR_SERVICE_JWT_SECRET` is absent or shorter than 32 bytes. Core LMS now has conditional public proxy routes configured with `AI_TUTOR_ENABLED=true`, the same dedicated secret and `AI_TUTOR_BASE_URL` pointing to the private FastAPI origin (for example, `http://127.0.0.1:8000` outside Docker). The FastAPI chat/status handlers are not implemented yet, so enabling the proxy does not make chat functional. Do not reuse the public end-user JWT signing key. Keep the private service off public gateway routes.

## Tests

```powershell
uv run --locked pytest -q
```

See [the Java/Python contract](docs/java-python-api.md) for assertion claims and private paths. The test suite exercises the FastAPI authentication dependency, conversation ownership and SQL constraints. No unimplemented status or send-turn route is exposed in the service.

The PostgreSQL migration integration test runs when `AI_TUTOR_TEST_DATABASE_URL` points to an **isolated, disposable** PostgreSQL database. It verifies fresh/repeat migration, checksum drift and the database constraints. Without that variable it is skipped by the normal `pytest` command.
