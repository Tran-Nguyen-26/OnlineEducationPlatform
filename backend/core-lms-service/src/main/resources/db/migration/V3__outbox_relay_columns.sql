-- Transactional outbox: relay backoff + last error (entity OutboxEvent)
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS next_attempt_at timestamp with time zone NOT NULL DEFAULT now();
ALTER TABLE outbox_events ADD COLUMN IF NOT EXISTS last_error text;
CREATE INDEX IF NOT EXISTS idx_outbox_events_due ON outbox_events (status, next_attempt_at);
