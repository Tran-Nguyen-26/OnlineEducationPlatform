BEGIN;

ALTER TABLE lesson_contents
    ADD COLUMN IF NOT EXISTS multipart_upload_id varchar(1024);

CREATE INDEX IF NOT EXISTS idx_lesson_contents_pending_upload_cleanup
    ON lesson_contents (updated_at)
    WHERE upload_status = 'PENDING';

COMMIT;
