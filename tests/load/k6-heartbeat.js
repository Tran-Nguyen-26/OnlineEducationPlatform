import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

/**
 * k6 Load Test Script — Stream L3 (Enrollment & Progress Heartbeat)
 * Target: 1.000 học viên gửi heartbeat mỗi 15s (≈ 67 RPS) trong 10 phút.
 * NFR: p95 response time <= 500ms, error rate < 1%.
 */

const errorRate = new Rate('error_rate');
const heartbeatLatency = new Trend('heartbeat_latency_ms');
const completedHeartbeats = new Counter('completed_heartbeats');

export const options = {
  scenarios: {
    heartbeat_steady: {
      executor: 'constant-arrival-rate',
      rate: 67, // 67 requests per second
      timeUnit: '1s',
      duration: '10m',
      preAllocatedVUs: 100,
      maxVUs: 500,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
    error_rate: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'https://staging-api.oep.internal';

export default function () {
  const dummyLessonId = '11111111-1111-1111-1111-111111111111';
  const payload = JSON.stringify({
    lessonId: dummyLessonId,
    progressPercent: Math.floor(Math.random() * 100),
    timestamp: new Date().toISOString(),
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${__ENV.TEST_JWT_TOKEN || 'dummy-token'}`,
    },
  };

  const startTime = Date.now();
  const res = http.post(`${BASE_URL}/api/learning/heartbeat`, payload, params);
  const duration = Date.now() - startTime;

  heartbeatLatency.add(duration);

  const passed = check(res, {
    'status is 200 or 204': (r) => r.status === 200 || r.status === 204,
    'latency is acceptable': () => duration <= 500,
  });

  errorRate.add(!passed);
  if (passed) {
    completedHeartbeats.add(1);
  }

  sleep(1);
}
