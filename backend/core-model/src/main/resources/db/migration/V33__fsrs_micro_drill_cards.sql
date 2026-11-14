CREATE TABLE IF NOT EXISTS learner_micro_drill_cards (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id uuid NOT NULL REFERENCES enrollments(id) ON DELETE CASCADE,
    course_id uuid NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    outcome_id uuid NOT NULL REFERENCES learning_outcomes(id) ON DELETE CASCADE,
    question_id uuid NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    due_at timestamptz NOT NULL DEFAULT now(),
    stability_days double precision NOT NULL DEFAULT 0.4 CHECK (stability_days > 0),
    difficulty double precision NOT NULL DEFAULT 5.0 CHECK (difficulty BETWEEN 1 AND 10),
    repetitions integer NOT NULL DEFAULT 0 CHECK (repetitions >= 0),
    lapses integer NOT NULL DEFAULT 0 CHECK (lapses >= 0),
    last_reviewed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_micro_drill_enrollment_question UNIQUE (enrollment_id, question_id)
);

CREATE INDEX IF NOT EXISTS idx_micro_drill_due ON learner_micro_drill_cards(enrollment_id, due_at);
