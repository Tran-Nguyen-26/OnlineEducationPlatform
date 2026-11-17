import http from 'k6/http';
import { check, sleep } from 'k6';

export const BASE = __ENV.BASE_URL || 'http://api-gateway:8080'; // inside the compose network, bypassing nginx
export const PASSWORD = __ENV.DEMO_PASSWORD;
export const USERS = Number(__ENV.USERS || 300);

// Each virtual user pretends to be a different client IP (the gateway trusts the last X-Forwarded-For hop when it
// is behind one proxy), so the per-IP login limit measures a realistic crowd rather than one machine.
export function ipFor(vu) {
  return `10.${(vu >> 16) & 255}.${(vu >> 8) & 255}.${vu & 255}`;
}

export function email(vu) {
  return `load${String(((vu - 1) % USERS) + 1).padStart(4, '0')}@oep.load`;
}

export function login(vu, extraHeaders = {}) {
  for (let attempt = 0; attempt < 5; attempt++) {
    const res = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email: email(vu), password: PASSWORD }), {
      headers: { 'Content-Type': 'application/json', 'X-Forwarded-For': ipFor(vu), ...extraHeaders },
      tags: { name: 'POST /api/auth/login' },
    });
    if (res.status === 200) return res.json('accessToken');
    if (res.status === 429) { sleep(Number(res.headers['Retry-After'] || 1)); continue; }
    sleep(1);
  }
  return null;
}

export function auth(token, vu, extra = {}) {
  return { headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json', 'X-Forwarded-For': ipFor(vu), ...extra } };
}

export function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

export function ok(res, name) {
  return check(res, { [`${name} 200`]: (r) => r.status === 200 });
}
