// Everybody behind ONE public IP (a classroom / campus NAT) logging in at the same moment.
import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE, email, PASSWORD } from './lib.js';

const n = Number(__ENV.STUDENTS || 100);
export const options = {
  scenarios: { burst: { executor: 'per-vu-iterations', vus: n, iterations: 1, maxDuration: '2m' } },
  summaryTrendStats: ['avg', 'med', 'p(95)', 'max'],
};
const ok = new Counter('login_ok');
const limited = new Counter('login_429');
const byGateway = new Counter('login_429_gateway_limiter');
const byLockout = new Counter('login_429_account_lockout');
const hasRetryAfter = new Counter('login_429_with_retry_after');

export default function () {
  let res;
  for (let i = 0; i < Number(__ENV.ATTEMPTS || 6); i++) { // a browser would retry after Retry-After (the frontend does)
    res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email: email(__VU), password: PASSWORD }),
      { headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': '203.0.113.7' } }); // same IP for all
    if (res.status === 200) { ok.add(1); return; }
    if (res.status === 429) { limited.add(1); if (res.body && res.body.indexOf('TOO_MANY_ATTEMPTS') >= 0) byLockout.add(1); else byGateway.add(1); if (res.headers['Retry-After']) hasRetryAfter.add(1); }
    else return;
    const wait = Number(res.headers['Retry-After'] || 1);
    sleep(wait);
  }
}
