import { check, sleep } from 'k6';
import exec from 'k6/execution';
import http from 'k6/http';
import { SharedArray } from 'k6/data';
import { Counter, Rate } from 'k6/metrics';

const target = (__ENV.TARGET_URL || '').replace(/\/$/, '');
const takeover = __ENV.STREAM_CASE === '1';
const users = Number(__ENV.LEARNERS || 300);
const samples = Number(__ENV.HEARTBEATS_PER_LEARNER || 20);
const cadenceSeconds = 30;
const path = __ENV.HEARTBEAT_PATH || '/api/progress/heartbeat';

if (!/^https?:\/\//.test(target) || !__ENV.HEARTBEAT_FIXTURE) {
  throw new Error('Set TARGET_URL and HEARTBEAT_FIXTURE to a test deployment and its learner fixtures.');
}
if (!path.startsWith('/') || path.startsWith('//')) {
  throw new Error('HEARTBEAT_PATH must be an application path.');
}
if (!Number.isInteger(users) || users < 1 || users > 1500 || !Number.isInteger(samples) || samples < 1) {
  throw new Error('LEARNERS must be 1–1500 and HEARTBEATS_PER_LEARNER must be a positive integer.');
}

const fixtures = new SharedArray('M05 learner fixtures', () => JSON.parse(open(__ENV.HEARTBEAT_FIXTURE)));
const failures = new Rate('heartbeat_errors');
const successes = new Counter('accepted_heartbeats');
const expectedCount = users * samples;

export const options = takeover
  ? { scenarios: { takeover: { executor: 'shared-iterations', vus: 1, iterations: 1 } },
      thresholds: { checks: ['rate==1'] } }
  : {
      discardResponseBodies: true,
      scenarios: {
        heartbeat: { executor: 'per-vu-iterations', vus: users, iterations: samples,
          maxDuration: (samples * cadenceSeconds + 60) + 's' },
      },
      thresholds: {
        'http_req_duration{name:heartbeat}': ['p(95)<100'],
        heartbeat_errors: ['rate<0.001'],
        iterations: ['count==' + expectedCount],
        accepted_heartbeats: ['count>' + (expectedCount * 0.999)],
      },
    };

export function setup() {
  if (takeover) {
    const fixture = fixtures[0];
    if (!fixture?.token || !fixture.firstOpen || !fixture.secondOpen ||
        !fixture.oldHeartbeat || !fixture.newHeartbeat) {
      throw new Error('STREAM_CASE requires one fixture with token, firstOpen, secondOpen, oldHeartbeat and newHeartbeat.');
    }
    return;
  }
  if (fixtures.length < users) throw new Error('Each simulated learner needs a fixture.');
  const learnerIds = new Set();
  for (let index = 0; index < users; index++) {
    const fixture = fixtures[index];
    if (!fixture.learnerId || !fixture.token || fixture.heartbeats?.length !== samples ||
        fixture.heartbeats.some((body) => !body || typeof body !== 'object' || Array.isArray(body)) ||
        new Set(fixture.heartbeats.map((body) => JSON.stringify(body))).size !== samples ||
        learnerIds.has(fixture.learnerId)) {
      throw new Error('Each learner fixture needs a distinct learnerId, token and one distinct body per heartbeat.');
    }
    learnerIds.add(fixture.learnerId);
  }
}

function post(pathname, token, body, name, expected = [200, 204]) {
  if (typeof pathname !== 'string' || !pathname.startsWith('/') || pathname.startsWith('//')) {
    throw new Error('Each request path must be relative to TARGET_URL.');
  }
  return http.post(target + pathname, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + token },
    redirects: 0,
    responseCallback: http.expectedStatuses(...expected),
    tags: { name },
  });
}

function accepted(response) {
  return response.status === 200 || response.status === 204;
}

function superseded(response) {
  if (response.status !== 409) return false;
  try {
    const body = response.json();
    return body.error === 'STREAM_SUPERSEDED' || body.code === 'STREAM_SUPERSEDED';
  } catch {
    return false;
  }
}

function checkTakeover() {
  const fixture = fixtures[0];
  const first = post(fixture.firstOpen.path, fixture.token, fixture.firstOpen.body, 'open_first', [200, 201, 204]);
  check(first, { 'first stream opened': (response) => accepted(response) || response.status === 201 });
  const second = post(fixture.secondOpen.path, fixture.token, fixture.secondOpen.body, 'open_second', [200, 201, 204]);
  check(second, { 'second stream opened': (response) => accepted(response) || response.status === 201 });
  check(post(path, fixture.token, fixture.oldHeartbeat, 'heartbeat_revoked', [409]), {
    'first stream receives 409 STREAM_SUPERSEDED': superseded,
  });
  check(post(path, fixture.token, fixture.newHeartbeat, 'heartbeat_current'), {
    'second stream heartbeat succeeds': accepted,
  });
}

export default function () {
  if (takeover) {
    checkTakeover();
    return;
  }
  const index = exec.vu.idInTest - 1;
  const iteration = exec.vu.iterationInScenario;
  if (iteration === 0) sleep(index * cadenceSeconds / users);

  const startedAt = Date.now();
  const fixture = fixtures[index];
  const ok = check(post(path, fixture.token, fixture.heartbeats[iteration], 'heartbeat'), {
    'heartbeat accepted': accepted,
  });
  failures.add(!ok);
  successes.add(Number(ok));
  if (iteration + 1 < samples) sleep(Math.max(0, cadenceSeconds - (Date.now() - startedAt) / 1000));
}
