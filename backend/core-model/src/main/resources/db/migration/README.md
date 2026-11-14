# SQL Migrations (Module Increment Scripts)

Hibernate `ddl-auto: update` remains the schema owner for development by agreement.
Flyway is not yet fully enabled across all production services; this directory stores the database schema migration increments.
A full Flyway baseline and switching Hibernate to `validate` are a separate task.

## Available Increment Scripts

- `V1__init_users_schema.sql`: Identity and user accounts base schema.
- `V3__enrollments_and_lesson_progress.sql`: M3 enrollment lifecycle and lesson progress tables & indexes. Creates missing enrollment/progress tables and adds indexes on existing Hibernate-created tables without deleting data.
- `V6__notifications_outbox_audit_logs.sql`: M6 notifications, outbox_events, audit_logs, and notification_preferences.
- `V9__access_codes.sql`: Active and Extension Codes plus enrollment expiry. Existing rows keep their access until an explicit expiry is assigned.
- `V18__registration_consent.sql`: explicit privacy consent metadata for newly registered learners.
- `V19__refresh_token_device_id.sql`: stable device identity for learner session limits.
- `V20__dormant_account_reactivation.sql`: last successful login timestamp for the dormant-account lifecycle.
- `V21__ai_mistake_explanation_cache.sql`: cache explanations for wrong quiz answers.
- `V22__ai_quiz_draft_bloom_level.sql`: keep Bloom level and source lesson on AI-generated question drafts.
- `V23__course_issue_flags.sql`: persist Instructor reports for Admin follow-up and resolution tracking.
- `V24__exam_panic_circuit_breaker.sql`: persisted Admin control that pauses new mass-exam attempts.
- `V25__notification_email_delivery.sql`: durable, deduplicated email delivery queue for notification events.
- `V26__default_quiz_assessment_kind.sql`: classify legacy quizzes with no assessment kind as `MINI_QUIZ` so they remain visible in learner assessments.
- `V27__ai_mistake_cache_usage_metrics.sql`: record anonymous cache hit/miss events for the Admin FinOps dashboard.
- `V28__ai_document_semantic_embeddings.sql`: enable pgvector storage and HNSW cosine search for lesson knowledge chunks.
- `V29__ai_usage_events.sql`: aggregate estimated token, latency and refusal metrics across AI Tutor, mistake explanations and AI question drafting.

## Execution

On the PRIMARY database, execute migrations using PostgreSQL's client in version order:

```sh
# Prerequisite: Initial users schema
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V1__init_users_schema.sql

# M3: Enrollments and lesson progress
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V3__enrollments_and_lesson_progress.sql

# M6: Notifications, Transactional Outbox, and System Audit Trail
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V6__notifications_outbox_audit_logs.sql

# IAM: Explicit consent metadata for learner registration
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V18__registration_consent.sql

# IAM: Stable browser/device identifiers on refresh-token sessions
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V19__refresh_token_device_id.sql

# IAM: Dormant-account lifecycle tracking
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V20__dormant_account_reactivation.sql

# AI Tutor: cached explanations for selected wrong quiz options
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V21__ai_mistake_explanation_cache.sql

# AI Quiz Generator: retain the Bloom level requested for each question
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V22__ai_quiz_draft_bloom_level.sql

# Admin queue for Instructor course-content and rubric flags
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V23__course_issue_flags.sql

# Admin control for pausing new mass-exam starts
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V24__exam_panic_circuit_breaker.sql

# Durable notification email queue (localized templates and retries)
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V25__notification_email_delivery.sql

# Quiz Engine: classify legacy quizzes without an assessment kind as mini-quizzes
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V26__default_quiz_assessment_kind.sql

# AI FinOps: aggregate mistake-explanation cache hits and misses
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V27__ai_mistake_cache_usage_metrics.sql

# AI Tutor: semantic lesson retrieval vectors (requires pgvector on PostgreSQL)
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V28__ai_document_semantic_embeddings.sql

# AI FinOps: estimated usage across AI features
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V29__ai_usage_events.sql

# Assignments: AI Viva Voce questions, responses, and instructor-facing consistency signal
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V30__assignment_ai_viva_voce.sql

# Verified lesson Q&A semantic index for FAQ deflection and AI Tutor retrieval
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V31__verified_qa_semantic_index.sql

# Learning analytics: anonymous 30-second video rewind heatmap aggregates
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V32__video_rewind_heatmap.sql

# Adaptive learning: per-learner FSRS micro-drill scheduling state
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V33__fsrs_micro_drill_cards.sql
```

### Notes on V3 (M3 SQL Migration)

On a new database, start core-lms-service to create the existing entity schema first.
Alternatively, provision the prerequisite identity/course/lesson tables separately.
Then apply V3 on the primary.

V3 creates missing enrollment/progress tables and adds the indexes on existing
Hibernate-created tables. It does not delete data or reconcile arbitrary legacy
column definitions. Duplicate rows cause the unique index creation to fail and the
transaction to roll back; resolve those explicitly rather than silently deleting data.
UUID values are generated by JPA. Preserve enrollment IDs when reactivating a student
so lesson progress and assessment foreign keys remain intact.

