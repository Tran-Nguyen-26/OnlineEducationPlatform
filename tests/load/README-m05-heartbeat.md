# M05 heartbeat load and replay tests

The test drives the core LMS API with actual JWTs, Redis and PostgreSQL. Apply
`V8__heartbeat_flush_batches.sql` to an existing schema before deploying. The
test harness creates and drops its own PostgreSQL schema. Use a dedicated Redis
database for the run, with no concurrent OEP workloads on that database.

## Playback contract

- `POST /api/progress/streams` accepts `enrollment_id`, `lesson_id`, and
  `video_id`. The API checks that the authenticated learner owns the active
  enrollment and that the video is available in the lesson. It returns
  `201` and a server-generated `stream_token`.
- `POST /api/progress/heartbeat` accepts `enrollment_id`, `lesson_id`,
  `stream_token`, a positive monotonically increasing `sequence` scoped to
  the stream, `current_playback_sec`, and `watched_delta_sec`. Send at 30-second
  intervals while playing. Reuse the sequence and body when retrying a request.
  An accepted or already processed sample returns `204`. A replaced or
  expired token returns `409 STREAM_SUPERSEDED`.
- Opening a new stream replaces the previous token for the learner. Only the
  active token can renew the 45-second lease and record progress.

## Run with seeded learners

The local fixture is a JSON array with a unique `token` and `enrollment_id`
per learner, plus `lesson_id` and `video_id`. Each token is an access JWT
valid for the full run. Use a video longer than the observation window to
measure regular flushes independently of the 80% milestone. Keep fixtures
containing JWTs outside version control.

```powershell
$env:TARGET_URL = 'http://localhost:8080'
$env:HEARTBEAT_FIXTURE = 'C:/test-fixtures/m05-heartbeats.json'
$env:LEARNERS = '300'
k6 run tests/load/k6-heartbeat.js
$env:LEARNERS = '1500'
k6 run tests/load/k6-heartbeat.js

$env:STREAM_CASE = '1'
k6 run tests/load/k6-heartbeat.js
Remove-Item Env:STREAM_CASE
```

Set `PROGRESS_PATH` if the gateway uses a different prefix. The steady scenario
stagger-starts the learners across 30 seconds, opens one stream each and sends
20 heartbeats per learner by default. The 300- and 1,500-learner cases sustain
approximately 10 and 50 heartbeat requests per second. It checks that all
iterations finish, openings succeed, p95 heartbeat latency is below 100 ms
and fewer than 0.1% of heartbeats fail. The separate takeover scenario checks
that the old token receives `409 STREAM_SUPERSEDED` and the new token succeeds.
Set `M05_SUMMARY_PATH` to save the k6 metrics as JSON.

## Run with automatic fixture provisioning

Set `OEP_TEST_DB_URL`, `OEP_TEST_DB_USER`, `OEP_TEST_DB_PASSWORD`,
`OEP_M05_REDIS_PORT` and `OEP_M05_REDIS_DB` for isolated test services.
Without k6 the suite verifies functional behavior. To also run both load
profiles, set `OEP_M05_K6` to the executable path, `OEP_M05_K6_SCRIPT` to the
absolute script path, `OEP_M05_REPORT_DIR` to an output directory, and
`OEP_M05_SAMPLES` to the sample count per learner. Use 20 for a full run.

```powershell
mvn -f backend/pom.xml -pl core-lms-service -am `
  -Dtest=M05HeartbeatPostgresTest -Dsurefire.failIfNoSpecifiedTests=false test
```

The harness flushes remaining samples after k6 exits. It exports the count
of accepted heartbeats, committed batch statements, and progress rows fed into
the batch upsert. Compare these independently against one synchronous upsert
per accepted heartbeat.

## Redis and recovery

The atomic Lua operation verifies the lease, deduplicates sequence numbers,
bounds credit by elapsed server time and buffers the most recent position with
the accumulated seconds. The flusher atomically separates new writes from the
snapshot in flight. PostgreSQL uses the batch UUID as an idempotency key.
The flusher acknowledges Redis only after the statement returns successfully.

Use a persistent Redis instance configured for a no-eviction policy. Pending
writes and per-video counters depend on Redis persistence. The application
defaults to a 120-second periodic flush and checks for an 80% milestone every
second. The task's current authorization uses existing active enrollments.
Archived access and quota enforcement depend on the M04 access lifecycle.
