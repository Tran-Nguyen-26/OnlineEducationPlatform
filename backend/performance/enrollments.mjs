// Node 20+. Credentials are read from a local JSON file, never printed.
import { readFile } from 'node:fs/promises';
import { performance } from 'node:perf_hooks';
import { setTimeout as sleep } from 'node:timers/promises';
import assert from 'node:assert/strict';

const [mode, file] = process.argv.slice(2);
assert(['burst', 'load'].includes(mode) && file, 'Usage: node enrollments.mjs burst|load /path/to/fixture.json');
const fixture = JSON.parse(await readFile(file, 'utf8'));
const base = (fixture.baseUrl || 'http://localhost:8081').replace(/\/$/, '');
const count = mode === 'burst' ? 200 : 1500; // 50 requests/s for 30 seconds
assert(fixture.tokens?.length >= count, `Need ${count} different student tokens`);
assert(fixture.courseId && fixture.ownerToken, 'Need courseId and ownerToken');
const subjects = fixture.tokens.slice(0, count).map(t => JSON.parse(Buffer.from(t.split('.')[1], 'base64url')).sub);
assert.equal(new Set(subjects).size, count, 'Use distinct students so idempotent retries cannot inflate successes');

async function roster() {
    const result = [];
    for (let page = 0; ; page++) {
        const response = await fetch(`${base}/api/enrollments?courseId=${fixture.courseId}&page=${page}&size=100`, {
            headers: { Authorization: `Bearer ${fixture.ownerToken}` }, signal: AbortSignal.timeout(60000),
        });
        assert.equal(response.status, 200, 'Owner roster request failed');
        const body = await response.json(); result.push(...body.content);
        if (body.last) return result;
    }
}
assert.equal((await roster()).length, 0, 'Use a fresh test course with no enrollments');
// Warm the HTTP connection and authorization path before starting the timed load.
await roster();
const start = performance.now();
async function enroll(index) {
    if (mode === 'load') await sleep(Math.max(0, start + index * 20 - performance.now()));
    const began = performance.now();
    const schedulingLag = mode === 'load' ? began - (start + index * 20) : 0;
    try {
        const response = await fetch(`${base}/api/enrollments`, {
            method: 'POST', headers: { Authorization: `Bearer ${fixture.tokens[index]}`, 'Content-Type': 'application/json' },
            body: JSON.stringify({ courseId: fixture.courseId }), signal: AbortSignal.timeout(60000),
        });
        const body = await response.json();
        return { status: response.status, error: body.error, id: body.id, ms: performance.now() - began, schedulingLag };
    } catch (error) {
        return { status: 0, error: error.name, ms: performance.now() - began, schedulingLag };
    }
}
const results = await Promise.all(Array.from({ length: count }, (_, i) => enroll(i)));
const sorted = results.map(r => r.ms).sort((a, b) => a - b);
const success = results.filter(r => r.status === 200);
const full = results.filter(r => r.status === 409 && r.error === 'COURSE_FULL');
const enrolled = await roster();
const occupied = enrolled.filter(e => ['ACTIVE', 'COMPLETED'].includes(e.status)).length;
const report = {
    mode, requests: count, successful: success.length, courseFull: full.length,
    unexpected: count - success.length - full.length, occupied,
    p95Ms: sorted[Math.ceil(count * 0.95) - 1],
    maxSchedulingLagMs: Math.max(...results.map(r => r.schedulingLag)),
    elapsedSeconds: (performance.now() - start) / 1000,
};
console.log(JSON.stringify(report, null, 2));
assert.equal(new Set(success.map(r => r.id)).size, success.length, 'Successes must create different enrollments');
assert.equal(report.unexpected, 0, 'Unexpected status/transport error');
if (mode === 'burst') {
    assert.equal(success.length, 50); assert.equal(full.length, 150); assert.equal(occupied, 50);
} else {
    assert.equal(success.length, count); assert.equal(occupied, count);
    assert(report.maxSchedulingLagMs < 100, 'Load generator could not maintain 50 RPS; result is invalid');
    assert(report.p95Ms <= 300, 'Enrollment p95 exceeds 300 ms');
}
