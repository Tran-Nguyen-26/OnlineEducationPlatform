import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

/**
 * k6 Load Test Script — Stream L4 (Quiz Concurrency)
 * Target: 1.000 học viên bắt đầu và nộp quiz 20 câu đồng thời trong 60s.
 * NFR: p95 response time <= 1.500ms, error rate < 1%.
 */

const errorRate = new Rate('error_rate');
const quizSubmitLatency = new Trend('quiz_submit_latency_ms');
const completedSubmissions = new Counter('completed_submissions');

export const options = {
  stages: [
    { duration: '30s', target: 500 },  // Ramp up to 500 VUs
    { duration: '60s', target: 1000 }, // Peak at 1,000 concurrent students
    { duration: '30s', target: 0 },    // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<1500', 'p(99)<3000'],
    error_rate: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.TARGET_URL || 'https://staging-api.oep.internal';

export default function () {
  const dummyQuizId = '22222222-2222-2222-2222-222222222222';
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${__ENV.TEST_JWT_TOKEN || 'dummy-token'}`,
  };

  // 1. Fetch quiz questions
  const quizRes = http.get(`${BASE_URL}/api/quizzes/${dummyQuizId}`, { headers });
  check(quizRes, { 'quiz fetched': (r) => r.status === 200 });

  sleep(Math.random() * 5 + 2);

  // 2. Submit 20 answers
  const answers = Array.from({ length: 20 }, (_, i) => ({
    questionId: `q-${i + 1}`,
    selectedOption: Math.floor(Math.random() * 4),
  }));

  const payload = JSON.stringify({
    quizId: dummyQuizId,
    answers: answers,
    submittedAt: new Date().toISOString(),
  });

  const startTime = Date.now();
  const submitRes = http.post(`${BASE_URL}/api/quizzes/${dummyQuizId}/submit`, payload, { headers });
  const duration = Date.now() - startTime;

  quizSubmitLatency.add(duration);

  const passed = check(submitRes, {
    'quiz submission succeeded': (r) => r.status === 200 || r.status === 201,
  });

  errorRate.add(!passed);
  if (passed) {
    completedSubmissions.add(1);
  }
}
