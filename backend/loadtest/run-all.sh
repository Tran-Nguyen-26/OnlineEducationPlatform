#!/usr/bin/env bash
# Full evidence run used for ../LOAD_TEST_REPORT.md (rebuilds the images, then runs every scenario). Takes ~20 min.
cd "$(dirname "$0")/.."
set -a; source .env.prod; set +a
C="docker compose -f docker-compose.prod.yml --env-file .env.prod"
$C build api-gateway core-lms-service assessment-service worker-service 2>&1 | tail -2
$C up -d 2>&1 | tail -1
for s in core-lms-service assessment-service worker-service api-gateway; do until [ "$(docker inspect -f '{{.State.Health.Status}}' oep-prod-$s-1)" = healthy ]; do sleep 4; done; done
RC="docker exec oep-prod-redis-1 redis-cli -a $REDIS_PASSWORD --no-auth-warning"
docker exec -i oep-prod-postgres-1 psql -U oep -d oep < loadtest/cleanup.sql >/dev/null
docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' > loadtest/results/docker-stats-idle.txt
$RC flushdb >/dev/null; echo "### login-nat"; ./loadtest/run.sh login-nat 2>&1 | grep -E "login_"
$RC flushdb >/dev/null; echo "### read-mix"; PEAK_VUS=200 ./loadtest/run.sh read-mix 2>&1 | grep -A14 THRESHOLDS
echo "### quiz-spike"; (sleep 14; docker stats --no-stream --format '{{.Name}} {{.CPUPerc}} {{.MemUsage}}' > loadtest/results/docker-stats-peak-quiz300.txt) & STUDENTS=300 ./loadtest/run.sh quiz-spike 2>&1 | grep -E "✓|✗|exam_completed|start_to_submit|http_req_duration|http_req_failed|iteration_duration"; wait
$RC flushdb >/dev/null; echo "### chaos"; FAULT=20 RECOVER=45 python3 loadtest/chaos.py 2>&1 | grep -E "^==|^\||^announcement|^report"
echo ALLDONE
