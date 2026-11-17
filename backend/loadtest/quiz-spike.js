// "Exam start": N students all log in, start the quiz within seconds of each other, think, then submit.
import http from 'k6/http';
import { sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';
import { BASE, login, auth, uuid, ok } from './lib.js';

const students = Number(__ENV.STUDENTS || 200);
export const options = {
  scenarios: {
    exam: { executor: 'per-vu-iterations', vus: students, iterations: 1, maxDuration: '5m' },
  },
  thresholds: {
    'http_req_duration{name:POST start-attempt}': ['p(95)<1500'],
    'http_req_duration{name:POST submit-attempt}': ['p(95)<2000'],
    'http_req_duration{name:POST /api/auth/login}': ['p(95)<10000'], // reported separately: login is the bcrypt-bound part
    http_req_failed: ['rate<0.01'],
    exam_completed: [`count>=${Math.floor(students * 0.99)}`],
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};
const completed = new Counter('exam_completed');
const startedIn = new Trend('start_to_submit_ms');

export function setup() {
  const t = login(1);
  const p = auth(t, 1);
  const en = http.get(`${BASE}/api/enrollments`, p).json();
  const english = en.find((e) => e.courseTitle === 'Tiếng Anh giao tiếp cơ bản');
  const quizzes = http.get(`${BASE}/api/quizzes?courseId=${english.courseId}`, p).json();
  return { quizId: quizzes[0].id };
}

export default function (data) {
  const vu = __VU;
  const token = login(vu);
  if (!token) return;
  const t0 = Date.now();
  const start = http.post(`${BASE}/api/quizzes/${data.quizId}/attempts`, '{}',
    { ...auth(token, vu, { 'Idempotency-Key': uuid() }), tags: { name: 'POST start-attempt' } });
  if (!ok(start, 'start')) return;
  const attempt = start.json();
  sleep(5 + Math.random() * 10); // students read and answer
  const answers = attempt.questions.map((q) => {
    const o = q.options || [];
    return { questionId: q.questionId, selectedOptionIds: o.length ? [o[0].id] : [], textAnswer: o.length ? null : 'much' };
  });
  const submit = http.post(`${BASE}/api/attempts/${attempt.attemptId}/submit`, JSON.stringify({ answers }),
    { ...auth(token, vu, { 'Idempotency-Key': uuid() }), tags: { name: 'POST submit-attempt' } });
  if (ok(submit, 'submit')) { completed.add(1); startedIn.add(Date.now() - t0); }
}
