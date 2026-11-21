# AI-Tutor implementation checkpoint

Updated: 2026-11-21 (Asia/Saigon). Branch: `dev-2`. Commits are local only; **do not push** on the user's behalf. Continue the ordered functional checklist in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md), test each finished slice carefully, and commit each finished slice before moving on. Leave the pre-existing untracked `frontend/test-results/` directory untouched.

## Implemented and committed

- `60c9dac` shared sequential lesson access; `07fc6e4` M7 relational ownership decision; `ef925e5` signed readable-lesson scope.
- `17c84af` authorized Java proxy routes; `41209d2` M7-owned V1 relational schema and migration runner; `ce4133a` private conversation/history routes; `a996e34` private course readiness; `c3e3d29` deterministic TEXT/PDF/Markdown/JSON parsers.
- `e276b72` clean frontend dependency install/build fix; `61665bd` M2/Admin upload validation (PDF 20 MiB, Markdown/JSON 5 MiB, stored object size/MIME verification).
- `8f7b3e7` Jina/OpenAI/Gemini embedding adapters; `b0b0c2c` Groq/OpenAI/Gemini generation adapters; `3b68757` durable chat claim/retry/replay and atomic citation-free abstentions.
- `1af8de3` M7 V2 native pgvector migration; `61c3bf3` exact scoped `PgvectorStore` adapter; `1315966` Pinecone adapter and shared vector-store tests; `484b4e1` durable versioned index-event receipt; `987861a` authenticated private job routes and indexing-service assertion; `78c5f09` synchronous M7 source deactivation; `79443e7` M2 unavailable-content hiding; `ef72895` M2 deletion handshake; `cf5c956` initial M2 index outbox emission; `40d6593` RabbitMQ delivery to the M7 job ledger; `ebba42e` eligible TEXT/metadata update handshake. The latest commit adds the file replacement handshake.

## Verification and environment

- Docker Desktop worked. Java full Maven suite passed with `-Duser.timezone=UTC` (the host timezone string is rejected by PostgreSQL), using the Maven cache on `E:\Caches\OnlineEducationPlatform\maven`: 54 tests, 0 failures/errors, 1 existing skip, including Testcontainers/Flyway.
- Frontend clean `npm ci` and `npm run build` passed with npm/Vite caches on E. `@types/node` and the lockfile were fixed in `e276b72` because the old lockfile could not be installed cleanly.
- Python uses `UV_CACHE_DIR=E:\Caches\OnlineEducationPlatform\uv` and `UV_PROJECT_ENVIRONMENT=E:\Caches\OnlineEducationPlatform\ai-tutor-venv`. The full suite passed 70/70 on a disposable `pgvector/pgvector:0.8.7-pg17` Docker container after synchronous M7 deactivation. The suite emits one upstream Starlette deprecation warning.
- AI provider tests use mocked HTTP; there are no live Jina/Groq/OpenAI/Gemini credentials in the workspace. Do not claim live-provider validation.
- RabbitMQ intake verification: 9 focused Python tests passed against a disposable broker and pgvector database, including duplicate delivery and dead-letter routing. The full M7 suite passed 79/79 on a fresh disposable pgvector database; the worker Maven suite passed 1 test with 1 existing video test skipped, and the focused M2 outbox tests passed 2/2. The existing upstream Starlette warning remains.
- M2 update-handshake verification: focused Java tests cover success, failure/retry, invalid TEXT, unrelated LINK and archived PDF metadata. The full core LMS suite passed 59/59 with Maven's `-Duser.timezone=UTC` and Docker-backed Flyway migration checks.
- File replacement verification: focused Java tests cover acknowledgement order, failure/retry, first upload version consistency, direct-completion rejection and unsupported inline upload routes. The full core LMS suite passed 64/64 with Testcontainers/Flyway after this change.
- Source-read contract verification: the full core LMS suite passed 73/73, including signed private route, scope/version/availability and file-grant tests. The M7 suite passed 82 tests with one existing RabbitMQ integration skip when no broker URL was supplied; the new source client tests passed 4/4. The new OpenAPI YAML parsed and its operation ID/200 response were checked. No live Java-to-Python process test has run yet.

## Resume from here

1. Check `git status --short` and the latest commit. The only expected untracked path after the pgvector adapter commit is `frontend/test-results/`.
2. Connect the authoritative private M2 source/version client to an asynchronous parse/embed/index worker for four supported formats, including bounded file download and a source-state recheck before activating a revision. The RabbitMQ queue and M7 durable job receipt are connected; job execution is still pending. Audit old in-flight file uploads when deploying the replacement handshake.
3. Implement scoped retrieval, grounded citations, provider integration and the private send-turn route using the existing claim ledger. The ledger currently commits only citation-free `ABSTAINED` turns; add server-validated citation snapshots before allowing `ANSWERED` persistence. Connect public React chat/history/status UI and verify end to end.
4. Complete functional tests and demo targets in the checklist; non-functional work follows afterward. Update docs and commit each finished, tested slice. Never push.

## User action

None required yet. For live provider integration later, request only the needed service API keys and deployment choices through the user's preferred secure configuration method. Do not put secrets in Git or test output.
