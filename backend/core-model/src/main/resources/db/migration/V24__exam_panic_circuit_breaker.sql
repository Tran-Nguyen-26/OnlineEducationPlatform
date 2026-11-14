BEGIN;

CREATE TABLE IF NOT EXISTS system_settings (
    setting_key varchar(100) PRIMARY KEY,
    boolean_value boolean NOT NULL,
    reason text,
    updated_by uuid REFERENCES users(id),
    updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO system_settings (setting_key, boolean_value, reason, updated_at)
VALUES ('EXAM_PANIC_CIRCUIT_BREAKER', false, 'Initial safe default', now())
ON CONFLICT (setting_key) DO NOTHING;

COMMIT;
