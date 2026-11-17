-- Distributed lock table for @SchedulerLock (one reminder run across all instances).
CREATE TABLE IF NOT EXISTS shedlock (
    name       varchar(64)  NOT NULL PRIMARY KEY,
    lock_until timestamptz  NOT NULL,
    locked_at  timestamptz  NOT NULL,
    locked_by  varchar(255) NOT NULL
);

-- Optimistic locking: concurrent edits of the same course/quiz no longer silently overwrite each other.
ALTER TABLE courses ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
ALTER TABLE quizzes ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;
