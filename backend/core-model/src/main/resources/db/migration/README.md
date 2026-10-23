# SQL Migrations (Module Increment Scripts)

Hibernate `ddl-auto: update` remains the schema owner for development by agreement.
Flyway is not yet enabled across all services; this directory stores the manual/production migration increments.

## Execution

On the PRIMARY database, execute the migration using PostgreSQL's client:

```sh
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V6__notifications_outbox_audit_logs.sql
```

## Available Increment Scripts

- `V3__enrollments_and_lesson_progress.sql`: M3 enrollment lifecycle and lesson progress tables & indexes.
- `V6__notifications_outbox_audit_logs.sql`: M6 notifications, outbox_events, audit_logs, and notification_preferences.
