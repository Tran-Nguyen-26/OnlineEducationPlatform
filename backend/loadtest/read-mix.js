// Steady browsing traffic: students look at their courses, catalogue, lessons and notifications.
// Run:  scripts via ./run.sh read-mix
import http from 'k6/http';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';
import { BASE, login, auth, ok } from './lib.js';

const peak = Number(__ENV.PEAK_VUS || 200);
export const options = {
  stages: [
    { duration: '45s', target: peak },
    { duration: '2m', target: peak },
    { duration: '15s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1500'],
    'http_req_duration{name:GET /api/courses}': ['p(95)<600'],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};
const throttled = new Counter('rate_limited_429');

let token = null, tokenAt = 0, courseId = null;

export default function () {
  const vu = __VU;
  if (!token || Date.now() - tokenAt > 600000) {
    token = login(vu);
    tokenAt = Date.now();
    if (!token) { sleep(2); return; }
  }
  const p = auth(token, vu);
  if (!courseId) {
    const en = http.get(`${BASE}/api/enrollments`, { ...p, tags: { name: 'GET /api/enrollments' } });
    if (en.status === 200 && en.json().length) courseId = en.json()[0].courseId;
  }
  const r = Math.random();
  let res;
  if (r < 0.30) res = http.get(`${BASE}/api/enrollments`, { ...p, tags: { name: 'GET /api/enrollments' } });
  else if (r < 0.55) res = http.get(`${BASE}/api/courses`, { ...p, tags: { name: 'GET /api/courses' } });
  else if (r < 0.75 && courseId) res = http.get(`${BASE}/api/courses/${courseId}/outline`, { ...p, tags: { name: 'GET /api/courses/{id}/outline' } });
  else if (r < 0.90) res = http.get(`${BASE}/api/notifications`, { ...p, tags: { name: 'GET /api/notifications' } });
  else res = http.get(`${BASE}/api/public/overview`, { tags: { name: 'GET /api/public/overview' } });
  if (res.status === 429) throttled.add(1);
  ok(res, 'read');
  sleep(1 + Math.random() * 2); // think time
}
