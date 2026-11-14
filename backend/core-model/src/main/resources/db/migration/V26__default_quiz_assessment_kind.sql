BEGIN;
UPDATE quizzes
SET assessment_kind = 'MINI_QUIZ'
WHERE assessment_kind IS NULL;
COMMIT;
