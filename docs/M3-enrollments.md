# M3: Enrollment API and capacity locking

Branch: `Quyet-BE-entity-api`. No merge or push is performed as part of implementation.

## API

All endpoints require a valid bearer JWT. The actor is taken from JWT `sub`, never
from a client-supplied student ID. Current account role/status is checked in the DB.

| Method | Path | Behavior |
| --- | --- | --- |
| POST | `/api/enrollments` | Body `{"courseId":"uuid"}`; enroll current STUDENT |
| POST | `/api/enrollments/{id}/withdraw` | Current student withdraws their own ACTIVE enrollment |
| POST | `/api/enrollments/{id}/remove` | Course OWNER with INSTRUCTOR role removes the enrollment |
| GET | `/api/enrollments?page=0&size=20` | Current student's own enrollments, including historical statuses |
| GET | `/api/enrollments?courseId=uuid&page=0&size=20` | Course roster, OWNER only |

Mutation success returns HTTP **200**, including creation and idempotent retries.
Response fields: `id`, `courseId`, `studentId`, `status`, `createdVia`, `enrolledAt`,
`completedAt`, `withdrawnAt`, `removedAt`. List responses contain Spring Page fields
including `content`, `totalElements`, `totalPages`, `number`, `size`, `last`.
Page is zero-based; size is 1–100; results sort by enrolledAt descending, then ID.

| Condition | HTTP |
| --- | --- |
| Missing authentication / invalid JWT subject | 401 |
| PRIVATE course, REMOVED student, disabled/wrong-role account, unauthorized actor | 403 |
| Course/enrollment not found in a mutation | 404 |
| Unpublished course, full course, invalid withdrawal state | 409 |
| Missing/invalid course UUID, malformed JSON, invalid pagination | 400 |

## State and permission rules

- New enrollment and WITHDRAWN reactivation require PUBLIC + PUBLISHED.
- ACTIVE and COMPLETED retries return the same enrollment even if the course has
  become private/unpublished/full. REMOVED always forbids self-enrollment.
- ACTIVE and COMPLETED consume capacity. Null capacity is unlimited; zero admits nobody.
- WITHDRAWN reactivation keeps the original ID, enrolledAt and all lesson progress;
  it clears withdrawnAt and changes status to ACTIVE. No progress rows are deleted.
- Withdrawal is allowed only for ACTIVE; retrying WITHDRAWN returns the same row
  and timestamp. COMPLETED/REMOVED withdrawal returns 409, preventing a ban bypass
  and avoiding reopening a completed record through withdrawal.
- OWNER can mark ACTIVE, COMPLETED or WITHDRAWN as REMOVED. Repeating removal keeps
  removedAt unchanged. Progress and assessment references remain intact.
- OWNER is determined by `course_instructors.course_role = OWNER`, not created_by.
  CO_INSTRUCTOR and ADMIN do not receive management permission. A course must be
  provisioned with its OWNER membership for roster/removal operations to work.
- Unknown or unauthorized course roster queries return 403 without exposing a roster.

## Concurrency

All three mutation paths lock the course row with JPA PESSIMISTIC_WRITE inside a
READ_COMMITTED transaction. Enrollment lookup/count happens after acquiring that
lock, and the lock is held until commit. Withdraw/remove first read only the course
ID, then acquire the course lock before loading enrollment state. This avoids
placing stale enrollment state in the persistence context before a concurrent write.

Occupancy uses `(course_id, status)` and counts ACTIVE + COMPLETED. A database unique
constraint on `(course_id, student_id)` is the second line of defense. Every future
capacity-changing flow (invites, approvals, imports, capacity edits) must follow the
same course-lock protocol; this guarantee does not cover arbitrary external SQL.
All operations currently use the primary, including list reads, to avoid replica lag
immediately after a write. Different courses lock independently.

## Schema

The existing Enrollment and LessonProgress entities are reused. The enrollment
entity now declares the capacity index. Hibernate `ddl-auto: update` remains enabled.
The SQL V3 file is manual and does not imply Flyway is installed or V1/V2 have run.
See [migration instructions](../backend/core-model/src/main/resources/db/migration/README.md).

## Verification

Java 21 and Maven are required. From the repository root:

```sh
mvn -f backend/pom.xml -pl core-lms-service -am test
mvn -f backend/pom.xml verify
```

The default suite has 32 service and 12 HTTP/security/validation tests and needs no
Docker/database. The 4 PostgreSQL tests are skipped unless explicitly configured.
CI now executes tests instead of passing `-DskipTests`.

Implementation verification: the full Maven reactor `verify` completed successfully
on Java 21; 44 tests passed, 4 opt-in PostgreSQL tests were skipped. The Node load
script passed `node --check`. No PostgreSQL concurrency or load run was performed.

To run the PostgreSQL tests later, point these environment variables at a dedicated
test database (PostgreSQL can be local or remote; Docker is not required):

```powershell
$env:OEP_TEST_DB_URL = 'jdbc:postgresql://localhost:5432/oep_test'
$env:OEP_TEST_DB_USER = 'test_user'
$env:OEP_TEST_DB_PASSWORD = 'test_password'
mvn -f backend/pom.xml -pl core-lms-service -am test
```

The test creates a randomly named `enroll_test_*` schema and drops that schema after
the suite. The user needs CREATE SCHEMA privileges. An interrupted run may leave its
test schema behind. It uses real signed JWTs and HTTP, asserts exactly 50 successes /
150 COURSE_FULL responses from 200 simultaneous requests, checks SQL counts, tests
concurrent retries, preserves lesson progress and reapplies SQL V3 twice.

Performance scripts and fixture preparation are in
[backend/performance](../backend/performance/README.md). PostgreSQL concurrency and
p95 <= 300 ms at 50 RPS have **not been measured in this environment**; these are
pending acceptance checks, not claims established by the mock-based unit tests.
