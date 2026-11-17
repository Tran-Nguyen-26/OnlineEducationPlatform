-- Removes quiz attempts/results made by load users so the quiz scenario can run again (keeps the users).
DELETE FROM quiz_results WHERE attempt_id IN (SELECT a.id FROM quiz_attempts a JOIN enrollments e ON e.id = a.enrollment_id JOIN users u ON u.id = e.student_id WHERE u.email LIKE '%@oep.load');
DELETE FROM quiz_answers WHERE attempt_id IN (SELECT a.id FROM quiz_attempts a JOIN enrollments e ON e.id = a.enrollment_id JOIN users u ON u.id = e.student_id WHERE u.email LIKE '%@oep.load');
DELETE FROM quiz_attempts WHERE enrollment_id IN (SELECT e.id FROM enrollments e JOIN users u ON u.id = e.student_id WHERE u.email LIKE '%@oep.load');
