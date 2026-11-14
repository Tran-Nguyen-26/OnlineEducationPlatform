BEGIN;

CREATE TABLE IF NOT EXISTS email_notification_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id),
    event_type varchar(100) NOT NULL,
    recipient_email varchar(255) NOT NULL,
    subject varchar(255) NOT NULL,
    message text NOT NULL,
    action_url varchar(500),
    locale varchar(10) NOT NULL DEFAULT 'vi-VN',
    status varchar(40) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    scheduled_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    dedupe_key varchar(255) NOT NULL UNIQUE,
    retry_count integer NOT NULL DEFAULT 0,
    last_error varchar(1000),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE email_notification_events ADD COLUMN IF NOT EXISTS subject varchar(255);
ALTER TABLE email_notification_events ADD COLUMN IF NOT EXISTS message text;
ALTER TABLE email_notification_events ADD COLUMN IF NOT EXISTS action_url varchar(500);
ALTER TABLE email_notification_events ADD COLUMN IF NOT EXISTS locale varchar(10) NOT NULL DEFAULT 'vi-VN';
ALTER TABLE email_notification_events ADD COLUMN IF NOT EXISTS last_error varchar(1000);
ALTER TABLE email_notification_events ADD COLUMN IF NOT EXISTS created_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE email_notification_events ADD COLUMN IF NOT EXISTS updated_at timestamptz NOT NULL DEFAULT now();

CREATE INDEX IF NOT EXISTS idx_email_notification_events_pending
    ON email_notification_events (status, scheduled_at) WHERE status = 'PENDING';

COMMIT;
