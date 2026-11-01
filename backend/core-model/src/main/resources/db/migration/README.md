# Manual database migrations

Hibernate `ddl-auto: update` remains the schema owner for local development. Flyway is not enabled across all production services; these PostgreSQL scripts are incremental and must be applied manually on the PRIMARY database after their prerequisites exist.

## Migration sequence

- `V1__init_users_schema.sql`: initial identity and user-account schema.
- `V2__course_content_schema.sql`: M2 course, instructor, module, lesson, content and change-log schema. Start Core LMS first so Hibernate creates the shared identity tables, then apply V2.
- `V3__enrollments_and_lesson_progress.sql`: M3 enrollment lifecycle, lesson progress and indexes. On a new database, create the prerequisite identity/course/lesson tables first.
- `V4__multipart_course_content_uploads.sql`: active multipart upload ID and partial index for the 24-hour pending-upload cleanup job. Hibernate also creates the nullable column in local development.
- `V6__notifications_outbox_audit_logs.sql`: M6 notifications, outbox events, audit logs and notification preferences.
- `explain_index_benchmark.sql`: optional query-plan benchmark; it is not a schema migration.

Example commands for the SQL increments:

```sh
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V1__init_users_schema.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V2__course_content_schema.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V3__enrollments_and_lesson_progress.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V4__multipart_course_content_uploads.sql
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V6__notifications_outbox_audit_logs.sql
```

The scripts do not delete existing data. Duplicate positions, duplicate enrollment rows, multiple default modules, or multiple course owners can make a unique-index step fail and roll back; resolve conflicting rows explicitly before retrying. These scripts do not reconcile arbitrary legacy column definitions and do not replace a production Flyway baseline or a provider-specific deployment plan.

For V4, apply the migration after the M2 tables exist. It records the active multipart upload ID and supports cleanup of pending files older than 24 hours. Upload objects are stored outside PostgreSQL.
