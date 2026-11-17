#!/usr/bin/env bash
# Runs one k6 scenario inside the prod compose network.  Usage: loadtest/run.sh <read-mix|quiz-spike|login-nat> [extra k6 args]
set -euo pipefail
cd "$(dirname "$0")/.."
SCEN="$1"; shift || true
set -a; source .env.prod; set +a
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p loadtest/results
docker run --rm --network oep-prod_oep -v "$PWD/loadtest":/work -w /work \
  -e DEMO_PASSWORD="$DEMO_PASSWORD" -e PEAK_VUS -e STUDENTS -e USERS -e ATTEMPTS \
  grafana/k6:latest run --quiet --summary-export "/work/results/${SCEN}-${STAMP}.json" "$@" "/work/${SCEN}.js" 2>&1 | tee "loadtest/results/${SCEN}-${STAMP}.txt"
