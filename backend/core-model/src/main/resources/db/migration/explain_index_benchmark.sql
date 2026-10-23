-- EXPLAIN ANALYZE Verification Script for M6 Notification Polling
-- Validates: SELECT * FROM notifications WHERE user_id = :userId AND is_read = false ORDER BY created_at DESC
-- Target: Index Scan using idx_notifications_user_id_is_read_created_at with p95 <= 50 ms across 100,000 notifications.

BEGIN;

-- 1. Setup temporary test data to verify execution plan
CREATE TEMP TABLE IF NOT EXISTS temp_benchmark_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    dedupe_key VARCHAR(255) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_temp_notifications_user_id_is_read_created_at
ON temp_benchmark_notifications (user_id, is_read, created_at DESC);

-- Generate 100,000 synthetic notifications distributed across 1,000 users
INSERT INTO temp_benchmark_notifications (user_id, event_type, title, message, dedupe_key, is_read, created_at)
SELECT
    ('00000000-0000-0000-0000-' || LPAD((i % 1000)::text, 12, '0'))::UUID,
    'QUIZ_PUBLISHED',
    'New quiz available',
    'Quiz notification content ' || i,
    'DEDUPE:' || i,
    (i % 5 = 0), -- 20% read, 80% unread
    now() - (i || ' seconds')::INTERVAL
FROM generate_series(1, 100000) AS s(i);

ANALYZE temp_benchmark_notifications;

-- 2. Execute EXPLAIN (ANALYZE, BUFFERS)
-- Verifies that Planner uses Index Scan directly without Sequential Scan.
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title, message, created_at
FROM temp_benchmark_notifications
WHERE user_id = '00000000-0000-0000-0000-000000000042'::UUID
  AND is_read = false
ORDER BY created_at DESC
LIMIT 20;

-- 3. Verify count query used by unread badge polling
EXPLAIN (ANALYZE, BUFFERS)
SELECT COUNT(*)
FROM temp_benchmark_notifications
WHERE user_id = '00000000-0000-0000-0000-000000000042'::UUID
  AND is_read = false;

ROLLBACK;
