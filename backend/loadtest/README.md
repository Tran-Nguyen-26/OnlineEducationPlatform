# loadtest/ — load and chaos tests

Runs against the **production-like Docker stack** (`docker-compose.prod.yml`, profile `prod,demo`). Results and analysis: `../LOAD_TEST_REPORT.md`.

## Prepare
```bash
scripts/gen-secrets.sh && docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
set -a; source .env.prod; set +a
docker exec -i oep-prod-postgres-1 psql -U oep -d oep -v n=300 < loadtest/seed-users.sql   # 300 students loadNNNN@oep.load (demo password)
```

## Scenarios (k6 in Docker, inside the compose network)
| Command | What it does | Pass criteria |
|---|---|---|
| `PEAK_VUS=200 loadtest/run.sh read-mix` | 200 concurrent students browsing (enrolments, catalogue, outline, notifications) for ~3 min | p95 < 500 ms, p99 < 1.5 s, errors < 1 % |
| `STUDENTS=300 loadtest/run.sh quiz-spike` | exam start: N students log in, start the quiz within seconds, think 5–15 s, submit | start p95 < 1.5 s, submit p95 < 2 s, ≥ 99 % complete, 0 errors |
| `loadtest/run.sh login-nat` | 100 students behind ONE public IP log in at the same instant | most get in via `Retry-After` retries |
| `psql … < loadtest/cleanup.sql` | removes the load users' quiz attempts so `quiz-spike` can run again | — |

Each virtual user sends its own `X-Forwarded-For`, i.e. behaves like a separate client address; k6 reaches the gateway directly (`api-gateway:8080`).
Summaries are written to `loadtest/results/<scenario>-<time>.{json,txt}`.

## Chaos test
```bash
FAULT=20 RECOVER=45 python3 loadtest/chaos.py [redis rabbitmq core-lms assessment postgres]
```
Probes four endpoints ~12 req/s through nginx while stopping one dependency for 20 s, then reports success rate, status codes and
recovery time per endpoint (`loadtest/results/chaos-*.md`). **It stops real containers** — run it only on the local stack.

## Everything at once
`loadtest/run-all.sh` rebuilds the Java images, then runs login-nat, read-mix, quiz-spike and all chaos scenarios (~20 min) and prints a summary.
