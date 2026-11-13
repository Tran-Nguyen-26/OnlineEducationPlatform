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
const prefix = __ENV.PROGRESS_PATH || '/api/progress';

if (!/^https?:\/\//.test(target) || !__ENV.HEARTBEAT_FIXTURE) {
  throw new Error('Set TARGET_URL and HEARTBEAT_FIXTURE to a test deployment and its learner fixtures.');
}
if (!prefix.startsWith('/') || prefix.startsWith('//')) {
  throw new Error('PROGRESS_PATH must be an application path.');
}
if (!Number.isInteger(users) || users < 1 || users > 1500 || !Number.isInteger(samples) || samples < 1) {
  throw new Error('LEARNERS must be 1–1500 and HEARTBEATS_PER_LEARNER must be a positive integer.');
}

const fixtures = new SharedArray('M05 learner fixtures', () => JSON.parse(open(__ENV.HEARTBEAT_FIXTURE)));
const failures = new Rate('heartbeat_errors');
const successes = new Counter('accepted_heartbeats');
const expectedCount = users * samples;
let streamToken;

export const options = takeover
  ? { scenarios: { takeover: { executor: 'shared-iterations', vus: 1, iterations: 1 } },
      thresholds: { checks: ['rate==1'] } }
  : {
      discardResponseBodies: true,
      scenarios: {
        heartbeat: { executor: 'per-vu-iterations', vus: users, iterations: samples,
          maxDuration: (samples * cadenceSeconds + 90) + 's' },
      },
      thresholds: {
        'http_req_duration{name:heartbeat}': ['p(95)<100'],
        heartbeat_errors: ['rate<0.001'],
        iterations: ['count==' + expectedCount],
        accepted_heartbeats: ['count>' + (expectedCount * 0.999)],
        'checks{name:open_stream}': ['rate==1'],
      },
    };

export function setup() {
  const required = takeover ? 1 : users;
  if (fixtures.length < required) throw new Error('Each simulated learner needs a fixture.');
  const enrollments = new Set();
  const tokens = new Set();
  for (let index = 0; index < required; index++) {
    const row = fixtures[index];
    if (!row.token || !row.enrollment_id || !row.lesson_id || !row.video_id ||
        enrollments.has(row.enrollment_id) || tokens.has(row.token)) {
      throw new Error('Each fixture needs its own token, enrollment_id, lesson_id and video_id.');
    }
    enrollments.add(row.enrollment_id);
    tokens.add(row.token);
  }
}

function post(path, fixture, body, name, status, responseType = 'none') {
  return http.post(target + prefix + path, JSON.stringify(body), {
    headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + fixture.token },
    redirects: 0,
    responseCallback: http.expectedStatuses(status),
    responseType,
    tags: { name },
  });
}

function openStream(fixture) {
  const response = post('/streams', fixture, {
    enrollment_id: fixture.enrollment_id, lesson_id: fixture.lesson_id, video_id: fixture.video_id,
  }, 'open_stream', 201, 'text');
  let token;
  if (response.status === 201) {
    try {
      token = response.json('stream_token');
    } catch {
      token = null;
    }
  }
  check(response, { 'stream opened': () => Boolean(token) }, { name: 'open_stream' });
  return token;
}

function sendHeartbeat(fixture, token, sequence, status = 204) {
  return post('/heartbeat', fixture, {
    enrollment_id: fixture.enrollment_id, lesson_id: fixture.lesson_id, stream_token: token,
    sequence, current_playback_sec: sequence * cadenceSeconds, watched_delta_sec: cadenceSeconds,
  }, 'heartbeat', status, status === 409 ? 'text' : 'none');
}

function checkTakeover() {
  const fixture = fixtures[0];
  const first = openStream(fixture);
  const second = openStream(fixture);
  if (!first || !second) return;
  check(sendHeartbeat(fixture, first, 1, 409), {
    'first stream receives 409 STREAM_SUPERSEDED': (response) =>
      response.status === 409 && response.json('error') === 'STREAM_SUPERSEDED',
  });
  check(sendHeartbeat(fixture, second, 1), {
    'second stream heartbeat succeeds': (response) => response.status === 204,
  });
}

export function handleSummary(data) {
  const result = JSON.stringify(data.metrics, null, 2) + '\n';
  const output = { stdout: result };
  if (__ENV.M05_SUMMARY_PATH) output[__ENV.M05_SUMMARY_PATH] = result;
  return output;
}

export default function () {
  if (takeover) {
    checkTakeover();
    return;
  }
  const index = exec.vu.idInTest - 1;
  const iteration = exec.vu.iterationInScenario;
  const fixture = fixtures[index];
  if (iteration === 0) {
    sleep(index * cadenceSeconds / users);
    streamToken = openStream(fixture);
    sleep(cadenceSeconds);
  }
  const startedAt = Date.now();
  const ok = Boolean(streamToken) && check(sendHeartbeat(fixture, streamToken, iteration + 1), {
    'heartbeat accepted': (response) => response.status === 204,
  });
  failures.add(!ok);
  successes.add(Number(ok));
  if (iteration + 1 < samples) sleep(Math.max(0, cadenceSeconds - (Date.now() - startedAt) / 1000));
}
