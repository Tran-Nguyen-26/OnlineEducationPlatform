# Manual database migrations

These PostgreSQL scripts are incremental changes for the existing development
schema. Hibernate `ddl-auto: update` remains the schema owner; Flyway is not
installed, so the scripts are not run automatically.

## V2 — Course and lesson content

Start Core LMS against the **primary** database first so Hibernate creates the
shared identity tables (`users`, `audit_logs`). Then apply V2 on that primary:

```sh
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f backend/core-model/src/main/resources/db/migration/V2__course_content_schema.sql
```

V2 creates the six M2 tables (`courses`, `course_instructors`, `course_modules`,
`lessons`, `lesson_contents`, `content_change_logs`) when absent, and adds the
position/lookup indexes, at-most-one-default-module rule and single-owner rule
when Hibernate already created those tables. It also updates the audit action
check to include the current shared `AuditAction` values, including Module 2
lifecycle actions.

The script is transactional and does not delete or rewrite course data. If
existing course/module/lesson positions conflict, a course has more than one
default module, or a course already has more than one OWNER association,
unique-index creation fails and the transaction rolls back. Resolve those
records explicitly before retrying. It does not
reconcile arbitrary legacy column definitions or serve as a full database
baseline.
