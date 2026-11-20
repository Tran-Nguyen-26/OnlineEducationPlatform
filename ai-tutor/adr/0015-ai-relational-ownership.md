# ADR 0015: M7 relational schema ownership

Status: Accepted for the M7 implementation (2026-11-20).

## Context

The core LMS Flyway `V1__baseline.sql` creates `public.ai_conversations`,
`public.ai_messages` and `public.ai_document_chunks`. The former `core-model`
JPA entities mapped those tables, so Hibernate's `ddl-auto: validate` expected
them at Spring startup. No Java repository, service or controller uses those
entities. Their layout does not support the agreed M7 owner/course binding,
source revisions, durable idempotency ledger, citations or pgvector storage.
Flyway V1 has already been applied in some environments and must remain
immutable.

## Decision

- FastAPI owns all new M7 relational tables in a dedicated PostgreSQL
  `ai_tutor` schema. Its own versioned migrations and migration history manage
  that schema; core LMS Flyway never creates or alters M7 tables. M7 tables use
  `ai_tutor`-qualified names in SQL, and the M7 database role has no write
  privilege on LMS-owned `public` tables.
- M7 stores `user_id`, `course_id`, `lesson_id` and `content_id` as externally
  validated UUIDs, without cross-service foreign keys. The signed per-request
  assertion and M2 indexing contract provide authority; M7 rechecks its own
  conversation ownership and source state. Course/enrollment/content truth
  remains in core LMS.
- Remove the unused Java AI entity mappings now, so Hibernate no longer
  validates or treats the old tables as active Java-owned models. Leave the
  historical `public.ai_*` tables in V1 untouched. New M7 code must never read
  or write them.
- Do not drop or copy historical rows automatically. Before any later cleanup,
  inspect each deployed database for rows, agree whether they need migration,
  take a backup, and use a separate forward-only LMS migration to retire the
  old tables. The M7 migration must not silently import their incompatible
  records.

## Consequences

The first M7 migration can create its tables without colliding with V1 and
without making Spring depend on M7's schema. Deployment must provision a
separate M7 migration role/schema and run M7 migrations before starting M7.
The legacy public tables remain as unused data until an audited cleanup.

The initial M7 migration now defines and tests the relational tables, constraints
and indexes. Vector storage and adapter-specific migrations remain separate.
