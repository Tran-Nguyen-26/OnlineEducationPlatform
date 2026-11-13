CREATE TABLE IF NOT EXISTS lesson_questions (
    id uuid PRIMARY KEY,
    lesson_id uuid NOT NULL REFERENCES lessons(id),
    author_id uuid NOT NULL REFERENCES users(id),
    video_id uuid REFERENCES lesson_contents(id) ON DELETE SET NULL,
    timestamp_sec integer NOT NULL CHECK (timestamp_sec >= 0),
    body text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_lesson_questions_lesson ON lesson_questions(lesson_id, created_at DESC, id);
CREATE TABLE IF NOT EXISTS lesson_replies (
    id uuid PRIMARY KEY,
    question_id uuid NOT NULL REFERENCES lesson_questions(id),
    author_id uuid NOT NULL REFERENCES users(id),
    body text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    verified boolean NOT NULL DEFAULT false
);
CREATE INDEX IF NOT EXISTS idx_lesson_replies_question ON lesson_replies(question_id, created_at, id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_lesson_reply_verified ON lesson_replies(question_id) WHERE verified;
