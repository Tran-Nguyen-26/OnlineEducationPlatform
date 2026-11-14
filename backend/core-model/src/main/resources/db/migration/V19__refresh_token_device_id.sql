BEGIN;

ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS device_id uuid;
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_device_active_created
    ON refresh_tokens(user_id, device_id, is_revoked, created_at);

COMMIT;
