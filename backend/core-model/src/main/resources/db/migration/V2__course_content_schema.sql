-- M2 incremental PostgreSQL schema, manually applied (Flyway is not enabled).
-- Prerequisites: users and audit_logs from the existing Core LMS schema.
-- Run with psql -v ON_ERROR_STOP=1 -f <this file> on the PRIMARY database.
BEGIN;

CREATE TABLE IF NOT EXISTS courses (
    id uuid PRIMARY KEY,
    title varchar(255) NOT NULL,
    description text,
    visibility varchar(40) NOT NULL,
    lifecycle_status varchar(40) NOT NULL,
    capacity integer,
    start_at timestamptz,
    end_at timestamptz,
    learning_mode varchar(40) NOT NULL,
    created_by uuid NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT courses_visibility_check CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT courses_lifecycle_status_check CHECK (lifecycle_status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED', 'DISABLED')),
    CONSTRAINT courses_learning_mode_check CHECK (learning_mode IN ('FREE_NAVIGATION', 'SEQUENTIAL'))
);

CREATE TABLE IF NOT EXISTS course_instructors (
    course_id uuid NOT NULL REFERENCES courses(id),
    instructor_id uuid NOT NULL REFERENCES users(id),
    course_role varchar(40) NOT NULL,
    added_by uuid REFERENCES users(id),
    added_at timestamptz NOT NULL,
    CONSTRAINT course_instructors_pkey PRIMARY KEY (course_id, instructor_id),
    CONSTRAINT course_instructors_course_role_check CHECK (course_role IN ('OWNER', 'CO_INSTRUCTOR'))
);

CREATE TABLE IF NOT EXISTS course_modules (
    id uuid PRIMARY KEY,
    course_id uuid NOT NULL REFERENCES courses(id),
    title varchar(255) NOT NULL,
    description text,
    position integer NOT NULL,
    is_default boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uk_course_modules_course_position UNIQUE (course_id, position)
);

CREATE TABLE IF NOT EXISTS lessons (
    id uuid PRIMARY KEY,
    module_id uuid NOT NULL REFERENCES course_modules(id),
    title varchar(255) NOT NULL,
    description text,
    position integer NOT NULL,
    is_required boolean NOT NULL,
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT uk_lessons_module_position UNIQUE (module_id, position)
);

CREATE TABLE IF NOT EXISTS lesson_contents (
    id uuid PRIMARY KEY,
    lesson_id uuid NOT NULL REFERENCES lessons(id),
    content_type varchar(40) NOT NULL,
    title varchar(255),
    text_content text,
    external_url varchar(1000),
    object_key varchar(500),
    file_name varchar(255),
    mime_type varchar(100),
    file_size bigint,
    duration_seconds integer,
    upload_status varchar(40) NOT NULL,
    position integer NOT NULL,
    version_number integer NOT NULL,
    created_by uuid REFERENCES users(id),
    updated_by uuid REFERENCES users(id),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    CONSTRAINT lesson_contents_content_type_check CHECK (content_type IN ('TEXT', 'LINK', 'VIDEO', 'PDF', 'SLIDE', 'DOCUMENT', 'FILE')),
    CONSTRAINT lesson_contents_upload_status_check CHECK (upload_status IN ('PENDING', 'UPLOADED', 'AVAILABLE', 'FAILED')),
    CONSTRAINT uk_lesson_contents_lesson_position UNIQUE (lesson_id, position)
);

CREATE TABLE IF NOT EXISTS content_change_logs (
    id uuid PRIMARY KEY,
    content_id uuid NOT NULL REFERENCES lesson_contents(id),
    changed_by uuid REFERENCES users(id),
    action varchar(50) NOT NULL,
    version_number integer NOT NULL,
    changed_at timestamptz NOT NULL
);

-- Keep these indexes aligned with the JPA entity declarations. The explicit
-- unique indexes also add those guarantees when Hibernate created the tables
-- before this script was applied.
CREATE INDEX IF NOT EXISTS idx_courses_lifecycle_status_visibility
    ON courses(lifecycle_status, visibility);
CREATE INDEX IF NOT EXISTS idx_courses_created_by ON courses(created_by);
CREATE INDEX IF NOT EXISTS idx_course_modules_course_id_position
    ON course_modules(course_id, position);
CREATE UNIQUE INDEX IF NOT EXISTS uk_course_modules_course_position
    ON course_modules(course_id, position);
CREATE UNIQUE INDEX IF NOT EXISTS uq_course_modules_single_default
    ON course_modules(course_id)
    WHERE is_default;
CREATE INDEX IF NOT EXISTS idx_lessons_module_id_position
    ON lessons(module_id, position);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lessons_module_position
    ON lessons(module_id, position);
CREATE INDEX IF NOT EXISTS idx_lesson_contents_lesson_id_position
    ON lesson_contents(lesson_id, position);
CREATE UNIQUE INDEX IF NOT EXISTS uk_lesson_contents_lesson_position
    ON lesson_contents(lesson_id, position);

-- A course can have at most one OWNER association. Existing duplicate owners
-- cause this index creation to fail and the transaction to roll back; resolve
-- those rows explicitly instead of silently changing ownership data.
CREATE UNIQUE INDEX IF NOT EXISTS uq_course_instructors_single_owner
    ON course_instructors(course_id)
    WHERE course_role = 'OWNER';

-- Extend the existing shared audit action check with the Module 2 lifecycle
-- actions. Keep the complete current AuditAction enum in this constraint so
-- replacing the old list does not remove actions owned by other modules.
DO $migration$
BEGIN
    IF to_regclass('public.audit_logs') IS NOT NULL THEN
        ALTER TABLE public.audit_logs
            DROP CONSTRAINT IF EXISTS audit_logs_action_check;
        ALTER TABLE public.audit_logs
            ADD CONSTRAINT audit_logs_action_check CHECK (action IN (
                'DISABLE_USER', 'ENABLE_USER', 'CHANGE_USER_ROLE',
                'REVOKE_ALL_SESSIONS', 'ADMIN_RESET_PASSWORD',
                'DISABLE_COURSE', 'ENABLE_COURSE', 'PUBLISH_COURSE',
                'UNPUBLISH_COURSE', 'ARCHIVE_COURSE', 'RESTORE_COURSE',
                'APPROVE_COURSE', 'REJECT_COURSE', 'TRANSFER_COURSE_OWNERSHIP',
                'ASSIGN_CO_INSTRUCTOR', 'REMOVE_CO_INSTRUCTOR',
                'FORCE_REMOVE_ENROLLMENT', 'ADMIN_MANUAL_ENROLL',
                'RESTORE_ENROLLMENT', 'RESET_QUIZ_ATTEMPT',
                'ADJUST_QUIZ_SCORE', 'UNLOCK_QUIZ_CONTENT', 'REGRADE',
                'ADJUST_SCORE', 'EXTEND_ASSIGNMENT_DEADLINE',
                'UPDATE_SYSTEM_SETTING', 'BROADCAST_SYSTEM_ALERT',
                'RETRY_FAILED_OUTBOX', 'MODERATE_HIDE_REVIEW',
                'UNHIDE_REVIEW', 'MUTE_CHANNEL_MEMBER',
                'APPROVE_INSTRUCTOR_APPLICATION',
                'REJECT_INSTRUCTOR_APPLICATION', 'REVOKE_INVITE_CODE',
                'TOGGLE_COURSE_AI_TUTOR', 'PURGE_AI_KNOWLEDGE_BASE'
            ));
    END IF;
END
$migration$;

COMMIT;
