# M05 heartbeat load test

Run against a test deployment with the day 9–15 M05 endpoint, seeded learners and
videos, Redis, and PostgreSQL. Set `TARGET_URL` explicitly. The script uses
`/api/progress/heartbeat` by default. Set `HEARTBEAT_PATH` if the deployed gateway
adds a prefix.

The local JSON fixture is an array. For the load case each entry has a unique
`learnerId`, a JWT in `token`, and a `heartbeats` array of valid request bodies in
order. Generate them for the deployed M05 contract with distinct idempotency keys
or sequence numbers and plausible video positions. Each learner needs an active
playback session when its first sample starts, valid for the first heartbeat
after staggering and renewable for the duration. Keep fixture files containing
JWTs outside version control.

```powershell
$env:TARGET_URL = 'http://localhost:8080'
$env:HEARTBEAT_FIXTURE = 'C:/test-fixtures/m05-heartbeats.json'
$env:LEARNERS = '300'
k6 run tests/load/k6-heartbeat.js
$env:LEARNERS = '1500'
k6 run tests/load/k6-heartbeat.js
```

The default 20 heartbeats per learner are spaced 30 seconds apart, with initial
sends staggered across the first 30 seconds. The 300- and 1,500-learner cases
produce approximately 10 and 50 requests per second. Pass criteria are p95
below 100 ms, error rate below 0.1%, and completion of every scheduled iteration.
All responses outside 200 and 204 count as failures for the steady load case.

For the single-user takeover check, set `STREAM_CASE=1` and use a one-entry
fixture. Its fields are `token`, `firstOpen` and `secondOpen` (each an object
with relative `path` and JSON `body`), plus the `oldHeartbeat` and
`newHeartbeat` JSON bodies for those devices. Use a fixture with stream IDs
known at request time. Run the same command and verify that the older heartbeat
receives 409 with `STREAM_SUPERSEDED` and the new heartbeat succeeds.

Run identical traffic before and after Redis buffering and batch upsert. Record
PostgreSQL write throughput and the API metrics from both runs so that the
write reduction is measured separately from the HTTP latency.
