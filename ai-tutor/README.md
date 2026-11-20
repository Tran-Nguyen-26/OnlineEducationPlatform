# M7 AI Tutor service

Python/FastAPI is a private service called by the Spring Boot backend. The first implementation slice provides a runnable service and verification of short-lived internal student assertions. Chat, indexing and public Java proxy routes are not implemented yet; track them in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md).

M7 owns its future PostgreSQL tables and migrations in the `ai_tutor` schema. The historical `public.ai_*` tables from the Java baseline are unused; see [ADR 0015](adr/0015-ai-relational-ownership.md). No M7 migration or database write is implemented yet.

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

See [the Java/Python contract](docs/java-python-api.md) for assertion claims and private paths. The test suite exercises the actual FastAPI authentication dependency through an isolated test route; no unimplemented chat route is exposed in the service.
