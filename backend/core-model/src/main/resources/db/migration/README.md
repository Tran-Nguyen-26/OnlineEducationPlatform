# SQL Migrations (Module Increment Scripts)

Hibernate `ddl-auto: update` remains the schema owner for development by agreement.
Flyway is not yet fully enabled across all production services; this directory stores the database schema migration increments.

## Execution

On the PRIMARY database, execute migrations using PostgreSQL's client in version order:

```sh
# Prerequisite: Initial users schema
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V1__init_users_schema.sql

# M3: Enrollments and lesson progress
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V3__enrollments_and_lesson_progress.sql

# M6: Notifications, Transactional Outbox, and System Audit Trail
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V6__notifications_outbox_audit_logs.sql
```

## Available Increment Scripts

- `V1__init_users_schema.sql`: Identity and user accounts base schema.
- `V3__enrollments_and_lesson_progress.sql`: M3 enrollment lifecycle and lesson progress tables & indexes. Creates missing enrollment/progress tables and adds indexes on existing Hibernate-created tables without deleting data.
- `V6__notifications_outbox_audit_logs.sql`: M6 notifications, outbox_events, audit_logs, and notification_preferences.
