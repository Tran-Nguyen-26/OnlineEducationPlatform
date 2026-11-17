-- Adaptive streaming (HLS) state of lesson videos.
ALTER TABLE lesson_contents ADD COLUMN IF NOT EXISTS transcode_status varchar(20);
ALTER TABLE lesson_contents ADD COLUMN IF NOT EXISTS hls_prefix varchar(500);
