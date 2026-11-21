# AI-Tutor implementation checkpoint

Updated: 2026-11-21 (Asia/Saigon). Branch: `dev-2`. Commits are local only; **do not push** on the user's behalf. Continue the ordered functional checklist in [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md), test each finished slice carefully, and commit each finished slice before moving on. Leave the pre-existing untracked `frontend/test-results/` directory untouched.

## Implemented and committed

- `60c9dac` shared sequential lesson access; `07fc6e4` M7 relational ownership decision; `ef925e5` signed readable-lesson scope.
- `17c84af` authorized Java proxy routes; `41209d2` M7-owned V1 relational schema and migration runner; `ce4133a` private conversation/history routes; `a996e34` private course readiness; `c3e3d29` deterministic TEXT/PDF/Markdown/JSON parsers.
- `e276b72` clean frontend dependency install/build fix; `61665bd` M2/Admin upload validation (PDF 20 MiB, Markdown/JSON 5 MiB, stored object size/MIME verification).
- `8f7b3e7` Jina/OpenAI/Gemini embedding adapters; `b0b0c2c` Groq/OpenAI/Gemini generation adapters; `3b68757` durable chat claim/retry/replay and atomic citation-free abstentions.
- `1af8de3` M7 V2 native pgvector migration; `61c3bf3` exact scoped `PgvectorStore` adapter; `1315966` Pinecone adapter and shared vector-store tests; `484b4e1` durable versioned index-event receipt; `987861a` authenticated private job routes and indexing-service assertion; `78c5f09` synchronous M7 source deactivation; `79443e7` M2 unavailable-content hiding. This checkpoint accompanies M2's deletion handshake.

## Verification and environment

- Docker Desktop worked. Java full Maven suite passed with `-Duser.timezone=UTC` (the host timezone string is rejected by PostgreSQL), using the Maven cache on `E:\Caches\OnlineEducationPlatform\maven`: 54 tests, 0 failures/errors, 1 existing skip, including Testcontainers/Flyway.
- Frontend clean `npm ci` and `npm run build` passed with npm/Vite caches on E. `@types/node` and the lockfile were fixed in `e276b72` because the old lockfile could not be installed cleanly.
- Python uses `UV_CACHE_DIR=E:\Caches\OnlineEducationPlatform\uv` and `UV_PROJECT_ENVIRONMENT=E:\Caches\OnlineEducationPlatform\ai-tutor-venv`. The full suite passed 70/70 on a disposable `pgvector/pgvector:0.8.7-pg17` Docker container after synchronous M7 deactivation. The suite emits one upstream Starlette deprecation warning.
- AI provider tests use mocked HTTP; there are no live Jina/Groq/OpenAI/Gemini credentials in the workspace. Do not claim live-provider validation.

## Resume from here

1. Check `git status --short` and the latest commit. The only expected untracked path after the pgvector adapter commit is `frontend/test-results/`.
2. Wire M2's transactional outbox to the authenticated M7 job receipt, with publication enqueue and atomic content version updates. Add the same unavailable-before-acknowledgement discipline for file replacement and TEXT edits. Then add the asynchronous parse/embed/index worker for four supported formats and recheck event ordering at source-state commit.
3. Implement scoped retrieval, grounded citations, provider integration and the private send-turn route using the existing claim ledger. The ledger currently commits only citation-free `ABSTAINED` turns; add server-validated citation snapshots before allowing `ANSWERED` persistence. Connect public React chat/history/status UI and verify end to end.
4. Complete functional tests and demo targets in the checklist; non-functional work follows afterward. Update docs and commit each finished, tested slice. Never push.

## User action

None required yet. For live provider integration later, request only the needed service API keys and deployment choices through the user's preferred secure configuration method. Do not put secrets in Git or test output.
