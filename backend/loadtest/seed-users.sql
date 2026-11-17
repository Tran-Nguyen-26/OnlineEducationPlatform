-- Creates N load-test students (loadNNN@oep.load, password = the demo password) enrolled in the two demo courses that have quizzes.
-- Usage: docker exec -i oep-prod-postgres-1 psql -U oep -d oep -v n=300 < loadtest/seed-users.sql
\set ON_ERROR_STOP on
WITH tmpl AS (SELECT password_hash FROM auth_identities ai JOIN users u ON u.id = ai.user_id WHERE u.email = 'hs01@oep.demo'),
new_users AS (
  INSERT INTO users (id, account_status, created_at, email, role, updated_at)
  SELECT gen_random_uuid(), 'ACTIVE', now(), 'load' || lpad(g::text, 4, '0') || '@oep.load', 'STUDENT', now()
  FROM generate_series(1, :n) g
  ON CONFLICT (email) DO NOTHING
  RETURNING id, email
),
ids AS (
  INSERT INTO auth_identities (id, created_at, must_change_password, password_hash, provider, user_id)
  SELECT gen_random_uuid(), now(), false, (SELECT password_hash FROM tmpl), 'LOCAL', id FROM new_users RETURNING user_id
),
prof AS (
  INSERT INTO user_profiles (user_id, full_name, updated_at) SELECT id, 'Load Student ' || email, now() FROM new_users RETURNING user_id
)
INSERT INTO enrollments (id, course_id, enrolled_at, invite_code_id, status, student_id)
SELECT gen_random_uuid(), c.id, now(), ic.id, 'ACTIVE', nu.id
FROM new_users nu
CROSS JOIN (SELECT id FROM courses WHERE title IN ('Tiếng Anh giao tiếp cơ bản', 'Kỹ năng thuyết trình')) c
JOIN course_invite_codes ic ON ic.course_id = c.id;
SELECT count(*) AS load_students FROM users WHERE email LIKE '%@oep.load';
