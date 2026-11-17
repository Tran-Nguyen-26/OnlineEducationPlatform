#!/usr/bin/env python3
"""Fault-injection test against the running prod-like stack (docker compose project oep-prod).

A prober sends ~12 requests/s through nginx -> gateway while one dependency at a time is stopped for FAULT seconds and
then started again. For each scenario it reports the success ratio per endpoint during the fault and the time until
each endpoint answered successfully again after the fault ended.

  python3 loadtest/chaos.py [scenario ...]     (default: all)   env: FAULT=20 RECOVER=40 BASE=http://localhost:8088
"""
import json, os, subprocess, sys, threading, time, urllib.request, urllib.error, uuid, collections, statistics

BASE = os.environ.get("BASE", "http://localhost:8088")
FAULT = int(os.environ.get("FAULT", "20"))
RECOVER = int(os.environ.get("RECOVER", "40"))
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
PROJECT = ["docker", "compose", "-f", os.path.join(ROOT, "docker-compose.prod.yml"), "--env-file", os.path.join(ROOT, ".env.prod")]
ENV = dict(l.strip().split("=", 1) for l in open(os.path.join(ROOT, ".env.prod")) if "=" in l and not l.startswith("#"))

def call(method, path, token=None, body=None, headers=None, timeout=8):
    h = {"Content-Type": "application/json", **(headers or {})}
    if token: h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, method=method, data=json.dumps(body).encode() if body is not None else None, headers=h)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r: return r.status, r.read()
    except urllib.error.HTTPError as e: return e.code, e.read()
    except Exception: return 0, b""   # connection refused / timeout

def login(email):
    c, d = call("POST", "/api/auth/login", body={"email": email, "password": ENV["DEMO_PASSWORD"]})
    assert c == 200, (email, c, d); return json.loads(d)["accessToken"]

def sh(*cmd): return subprocess.run(list(cmd), capture_output=True, text=True).stdout.strip()
def psql(q): return sh(*PROJECT, "exec", "-T", "postgres", "psql", "-U", ENV["DB_USER"], "-d", ENV["DB_NAME"], "-At", "-c", q)

student = login("hs01@oep.demo")
course = json.loads(call("GET", "/api/enrollments", student)[1])[0]["courseId"]
teacher = login("gv.lan@oep.demo")
teacher_course = json.loads(call("GET", "/api/instructors/me/courses", teacher)[1])[0]["id"]

PROBES = {
    "public overview (anon, cached)": lambda: call("GET", "/api/public/overview"),
    "my enrollments (core-lms + DB)": lambda: call("GET", "/api/enrollments", student),
    "quizzes (assessment + DB)": lambda: call("GET", f"/api/quizzes?courseId={course}", student),
    "POST + Idempotency-Key (redis)": lambda: call("POST", "/api/users/me/avatar/upload-url", student,
                                                  {"fileName": "a.png", "mimeType": "image/png", "fileSize": 100}, {"Idempotency-Key": str(uuid.uuid4())}),
}
samples = []          # (t, probe, status, latency)
stop = threading.Event()

INTERVAL = {"POST + Idempotency-Key (redis)": 0.7}   # the upload-url route is limited to 2 req/s per user

def prober(name, fn):
    while not stop.is_set():
        t0 = time.time(); status, _ = fn(); samples.append((t0, name, status, time.time() - t0))
        time.sleep(max(0, INTERVAL.get(name, 0.33) - (time.time() - t0)))

for n, f in PROBES.items(): threading.Thread(target=prober, args=(n, f), daemon=True).start()

def window(a, b, name): return [s for s in samples if a <= s[0] < b and s[1] == name]
def ok(s): return 200 <= s < 300

SCEN = {
    "redis":      dict(stop=["redis"],              note="cache, token revocation, rate limiter, idempotency, login lockout"),
    "rabbitmq":   dict(stop=["rabbitmq"],           note="async notifications (outbox must absorb them)"),
    "core-lms":   dict(stop=["core-lms-service"],   note="users/courses service (circuit breaker + fallback)"),
    "assessment": dict(stop=["assessment-service"], note="quiz service"),
    "postgres":   dict(stop=["postgres"],           note="the database itself"),
    "gateway":    dict(stop=["api-gateway"],        note="the gateway is stopped, then RECREATED (new container IP) — nginx must re-resolve it", heal=["up", "-d", "--force-recreate", "api-gateway"]),
}
selected = sys.argv[1:] or list(SCEN)
report = [f"# Chaos test — {time.strftime('%Y-%m-%d %H:%M')}\n", f"Prober ~12 req/s via {BASE}; each dependency stopped {FAULT}s, then {RECOVER}s observed.\n"]
time.sleep(8)  # baseline
for name in selected:
    sc = SCEN[name]; print(f"== {name}", flush=True)
    time.sleep(5)
    pending_before = psql("select count(*) from outbox_events where status='PENDING'") if name == "rabbitmq" else None
    t_fault = time.time()
    sh(*PROJECT, "stop", *sc["stop"])
    extra = ""
    if name == "rabbitmq":   # a business action while the broker is down
        time.sleep(3)
        c, _ = call("POST", f"/api/courses/{teacher_course}/announcements", teacher, {"title": "chaos " + str(int(time.time())), "content": "broker down"}, {"Idempotency-Key": str(uuid.uuid4())})
        extra = f"announcement while broker down: HTTP {c}; outbox pending={psql('select count(*) from outbox_events where status=$$PENDING$$')}"
    time.sleep(max(0, FAULT - (time.time() - t_fault)))
    t_heal = time.time()
    sh(*PROJECT, *(sc.get("heal") or ["start", *sc["stop"]]))
    time.sleep(RECOVER)
    if name == "rabbitmq":
        extra += f"; after recovery pending={psql('select count(*) from outbox_events where status=$$PENDING$$')}"
    report += [f"\n## {name} down — {sc['note']}\n", "| endpoint | success during fault | statuses during fault | statuses after heal | recovered after (s) | p50 latency fault (ms) | p95 latency after heal (ms) |", "|---|---|---|---|---|---|---|"]
    for p in PROBES:
        w = window(t_fault + 2, t_heal, p); after = [s for s in window(t_heal, t_heal + RECOVER, p) if ok(s[2])]
        # recovery = first success after which no failure occurs for the rest of the window
        later = window(t_heal, t_heal + RECOVER, p); rec = None
        for i, s in enumerate(later):
            if ok(s[2]) and all(ok(x[2]) for x in later[i:]): rec = s[0] - t_heal; break
        pct = 100 * sum(ok(s[2]) for s in w) / max(1, len(w))
        codes = ", ".join(f"{k}×{v}" for k, v in collections.Counter(s[2] for s in w).most_common())
        lat = statistics.median([s[3] * 1000 for s in w]) if w else 0
        codes_after = ", ".join(f"{k}×{v}" for k, v in collections.Counter(s[2] for s in later).most_common())
        lat_after = sorted(s[3] * 1000 for s in later)[int(0.95 * (len(later) - 1))] if later else 0
        report.append(f"| {p} | {pct:.0f}% ({len(w)} req) | {codes} | {codes_after} | {'—' if rec is None else f'{rec:.0f}'} | {lat:.0f} | {lat_after:.0f} |")
    if extra: report.append(f"\n{extra}\n")
    print("\n".join(report[-8:]), flush=True)
stop.set()
if os.environ.get('DEBUG429'):
    t00 = samples[0][0]
    for s_ in samples:
        if s_[2] == 429: print('429 at +%.1fs on %s' % (s_[0] - t00, s_[1]))
out = os.path.join(ROOT, "loadtest", "results", f"chaos-{time.strftime('%Y%m%d-%H%M%S')}.md")
open(out, "w").write("\n".join(report) + "\n"); print("report:", out)
