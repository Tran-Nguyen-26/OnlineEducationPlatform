-- M6 incremental PostgreSQL script for Notifications, Transactional Outbox, and System Audit Trail.
-- Module 6: Async Notifications, Polling API, Outbox Pattern and System Audit Trail
-- Prerequisites: users table from identity module.
-- Run with psql -v ON_ERROR_STOP=1 -f <this file> on the PRIMARY database.

BEGIN;

-- 1. Notifications Table
CREATE TABLE IF NOT EXISTS notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    event_type VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    dedupe_key VARCHAR(255) NOT NULL,
    target_type VARCHAR(50),
    target_id UUID,
    action_url VARCHAR(500),
    metadata JSONB,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notifications_dedupe_key UNIQUE (dedupe_key)
);

-- Core composite index for fast inbox polling (user_id, is_read, created_at)
CREATE INDEX IF NOT EXISTS idx_notifications_user_id_is_read_created_at
ON notifications (user_id, is_read, created_at DESC);

-- Unique index enforcing deduplication across retried workers
CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_dedupe_key
ON notifications (dedupe_key);

-- Partial index for 90-day read notifications archival/cleanup cron
CREATE INDEX IF NOT EXISTS idx_notifications_cleanup
ON notifications (is_read, created_at)
WHERE is_read = true;


-- 2. Transactional Outbox Events Table
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);

-- Standard index for outbox relay / polling
CREATE INDEX IF NOT EXISTS idx_outbox_events_status_created_at
ON outbox_events (status, created_at);

-- Partial index optimizing active outbox polling for PENDING events
CREATE INDEX IF NOT EXISTS idx_outbox_events_pending
ON outbox_events (status, created_at)
WHERE status = 'PENDING';


-- 3. System Audit Trail Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id UUID NOT NULL REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id UUID NOT NULL,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Forensic & administrative query indexes
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor_id
ON audit_logs (actor_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_target_type_target_id
ON audit_logs (target_type, target_id);

CREATE INDEX IF NOT EXISTS idx_audit_logs_action_created_at
ON audit_logs (action, created_at DESC);


-- 4. User Notification Delivery Preferences Table
CREATE TABLE IF NOT EXISTS notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    event_type VARCHAR(100) NOT NULL,
    in_app_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reminder_before_minutes INT DEFAULT 1440,
    CONSTRAINT uq_notification_preferences_user_id_event_type UNIQUE (user_id, event_type)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_notification_preferences_user_id_event_type
ON notification_preferences (user_id, event_type);

CREATE INDEX IF NOT EXISTS idx_notification_preferences_user_id
ON notification_preferences (user_id);

COMMIT;
