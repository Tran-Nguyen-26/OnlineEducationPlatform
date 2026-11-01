# Enrollment load checks (deferred; no measured result yet)

Requires Node 20+, a running Core LMS and a dedicated PostgreSQL test database.
No Docker dependency is required if PostgreSQL runs locally or remotely.
Do not use production data. These scripts create real enrollments.

Prepare a fresh PUBLIC/PUBLISHED course with an INSTRUCTOR in course_instructors
as OWNER. Use capacity 50 for `burst`, capacity >= 1500 for `load`. Prepare 200 or
1500 distinct ACTIVE STUDENT accounts with valid JWTs. For each run use a new course;
the script refuses courses that already contain any enrollment.

Create an untracked `fixture.local.json`:

```json
{
  "baseUrl": "http://localhost:8081",
  "courseId": "course-uuid",
  "ownerToken": "owner-jwt",
  "tokens": ["student-1-jwt", "student-2-jwt"]
}
```

```sh
node backend/performance/enrollments.mjs burst backend/performance/fixture.local.json
node backend/performance/enrollments.mjs load backend/performance/fixture.local.json
```

`burst` starts 200 HTTP requests together; asserts 50 HTTP 200, 150 COURSE_FULL/409,
50 distinct enrollment IDs and 50 occupied seats in the primary-backed owner roster.
`load` schedules 50 new enrollments/s for 30 seconds; asserts every request succeeds,
p95 <= 300 ms and generator scheduling lag < 100 ms. It does not reuse enrolled
students, so the benchmark exercises writes instead of the idempotent fast path.
Duration includes reading the response body. JWT issuance is outside the measurement.
The script warms the connection with roster reads; report this alongside machine,
database, pool size, network, commit, JVM settings and JSON output when sharing results.

Default target is Core LMS :8081. If testing through gateway :8080, report that
separately and ensure rate limits do not invalidate the run. A 429 is a test failure.
The separate opt-in EnrollmentPostgresTest also verifies persisted counts using SQL.
