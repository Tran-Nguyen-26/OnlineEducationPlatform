CREATE TABLE IF NOT EXISTS archived_video_usage (
    user_id uuid NOT NULL REFERENCES users(id),
    usage_month date NOT NULL,
    seconds_used integer NOT NULL CHECK (seconds_used BETWEEN 0 AND 54000),
    PRIMARY KEY (user_id, usage_month)
);
