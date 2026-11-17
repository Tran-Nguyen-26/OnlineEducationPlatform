#!/usr/bin/env bash
# End-to-end smoke test against the running production-like stack. Usage: scripts/smoke.sh [base_url]
set -uo pipefail
cd "$(dirname "$0")/.."
BASE="${1:-http://localhost:8088}"
set -a; source .env.prod; set +a
PASS=0; FAIL=0
ok()   { echo "  PASS  $1"; PASS=$((PASS+1)); }
bad()  { echo "  FAIL  $1  ($2)"; FAIL=$((FAIL+1)); }
chk()  { [[ "$2" == "$3" ]] && ok "$1" || bad "$1" "expected $3, got $2"; }
code() { curl -s -o /dev/null -w '%{http_code}' "$@"; }

chk "frontend serves the SPA"                       "$(code "$BASE/")" 200
chk "SPA deep link falls back to index.html"        "$(code "$BASE/student/courses")" 200
chk "public API reachable through nginx -> gateway" "$(code "$BASE/api/public/overview")" 200
chk "API v1 alias"                                  "$(code "$BASE/api/v1/public/overview")" 200
chk "protected API without token is 401"            "$(code "$BASE/api/enrollments")" 401
curl -s "$BASE/actuator/prometheus" | grep -q "jvm_memory" && bad "metrics must not be public" exposed || ok "metrics not reachable from outside"
curl -sI "$BASE/" | grep -qi "content-security-policy" && ok "CSP header on the SPA" || bad "CSP header" missing

TOKEN_JSON=$(curl -s -X POST "$BASE/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$ADMIN_SEED_EMAIL\",\"password\":\"$ADMIN_SEED_PASSWORD\"}")
echo "$TOKEN_JSON" | grep -q accessToken && ok "admin login with the generated password" || bad "admin login" "$TOKEN_JSON"
TOKEN=$(echo "$TOKEN_JSON" | sed -E 's/.*"accessToken":"([^"]+)".*/\1/')
chk "forced password change blocks other APIs"      "$(code -H "Authorization: Bearer $TOKEN" "$BASE/api/admin/users")" 403

for svc in api-gateway core-lms-service assessment-service worker-service; do
  s=$(docker compose -f docker-compose.prod.yml --env-file .env.prod exec -T $svc curl -fs http://127.0.0.1:9090/actuator/health/readiness 2>/dev/null)
  [[ "$s" == *UP* ]] && ok "$svc readiness UP (management port)" || bad "$svc readiness" "$s"
done
echo; echo "passed=$PASS failed=$FAIL"; [[ $FAIL -eq 0 ]]
