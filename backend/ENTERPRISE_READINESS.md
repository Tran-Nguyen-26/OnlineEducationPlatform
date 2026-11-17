# ENTERPRISE_READINESS.md — Năng lực xuyên suốt (cross-cutting) của một phần mềm chuẩn doanh nghiệp

Đã đối chiếu từng mục với code/cấu hình thật (commit `6d79ca8`). Ký hiệu: ✅ có và dùng được · ⚠️ có một phần · ❌ chưa có.

## 1. Hiện trạng

### 1.1. Độ tin cậy & chịu lỗi
| Năng lực | Hiện trạng thật | |
|---|---|---|
| **Idempotency** | Chỉ có ở *consumer* thông báo (`dedupeKey` unique). API ghi không nhận `Idempotency-Key`. Chống nộp quiz đúp bằng kiểm tra trạng thái + ràng buộc unique (đã thử song song: an toàn). Cột `quiz_attempts.idempotency_key` có nhưng không dùng. **Ghi nhận học phí / tạo phúc khảo / hoàn thành khóa / gửi thông báo hệ thống nếu client retry sẽ tạo bản ghi trùng** | ⚠️ |
| **Retry / backoff / timeout** | Không có `@Retryable`, Resilience4j, timeout HTTP/S3 hay timeout truy vấn DB. Consumer RabbitMQ không cấu hình retry: ngoại lệ ⇒ **requeue vô hạn** (poison message có thể kẹt hàng đợi) | ❌ |
| **Dead-letter / parking lot** | Không | ❌ |
| **Circuit breaker / fallback** | Không (gateway → service chết thì trả lỗi thô) | ❌ |
| **Transactional outbox** | Entity `OutboxEvent` + enum có sẵn **nhưng không dùng**. 9 chỗ `convertAndSend` nằm *trong* transaction nghiệp vụ ⇒ RabbitMQ chết thì **đăng thông báo khóa/đổi điểm… thất bại theo**; transaction rollback sau khi đã gửi ⇒ thông báo "ma" | ❌ |
| Rate limit | Gateway: 20 req/s, burst 40, theo user (hoặc IP), lưu Redis. **Không có chính sách riêng** cho đăng nhập/đăng ký/quên mật khẩu/upload/nộp quiz; không `Retry-After`; không khóa tài khoản khi đăng nhập sai | ⚠️ |
| Khóa scheduler khi chạy nhiều bản | Không có ShedLock ⇒ cron nhắc hạn sẽ **chạy trùng** khi scale >1 instance (đã có dedupe ở consumer nên chỉ phí tài nguyên) | ⚠️ |
| Graceful shutdown, readiness/liveness | Chưa cấu hình (`server.shutdown`, probes); actuator chỉ `health,info` | ❌ |
| Khóa lạc quan (`@Version`) | Không có ⇒ hai admin sửa cùng khóa/quiz: ghi đè mất dữ liệu im lặng | ❌ |
| Khóa dòng khi cạnh tranh | ✅ mới thêm cho ghi danh/xếp chỗ chờ; unique constraint cho quiz/submit | ✅ một phần |

### 1.2. Quan sát (observability)
| Năng lực | Hiện trạng | |
|---|---|---|
| Log có cấu trúc / correlation id | Không cấu hình logging nào (log mặc định dạng text, không `requestId`, không MDC, không che PII) | ❌ |
| Metrics (Micrometer/Prometheus) | Có actuator nhưng **không** mở `/metrics`/`/prometheus`; không metric nghiệp vụ | ❌ |
| Tracing phân tán | Không | ❌ |
| Dashboard / cảnh báo / SLO | Không | ❌ |
| Audit log | ✅ Có cho thao tác Admin (đã dịch nhãn tiếng Việt). **Thiếu:** đăng nhập thành công/thất bại, chấm điểm/sửa điểm, phúc khảo, đổi mật khẩu | ⚠️ |

### 1.3. Bảo mật
| Năng lực | Hiện trạng | |
|---|---|---|
| Xác thực/RBAC/kiểm tra sở hữu | ✅ Đã rà: 133 endpoint × 4 vai trò + 41 ca truy cập chéo đều đạt | ✅ |
| Băm mật khẩu | ✅ BCrypt | ✅ |
| **Quản lý khóa JWT** | **Cặp khóa RSA sinh mới mỗi lần khởi động `core-lms`** ⇒ restart/scale = **mọi token bị vô hiệu**, các instance không thống nhất. Không `kid`/xoay khóa | ❌ nghiêm trọng |
| Thu hồi token | Access 15 phút; refresh lưu hash, có thu hồi. Khóa tài khoản/đổi mật khẩu **không** vô hiệu access token đang sống | ⚠️ |
| Security headers (HSTS, CSP, nosniff, frame-options, referrer) | Không | ❌ |
| Bí mật/cấu hình theo môi trường | Mật khẩu mặc định (`oep`, `Admin@12345`, khóa MinIO) nằm trong `application.yml`; chưa tách profile dev/staging/prod | ❌ |
| Khóa tài khoản / chính sách mật khẩu | Chỉ `min 8 ký tự` | ❌ |
| Kiểm tra file upload | Chỉ chặn phần mở rộng ở Q&A, ảnh đại diện (loại + 2MB); nội dung bài/bài nộp **không giới hạn dung lượng/loại phía server**, không quét mã độc | ⚠️ |
| Quét phụ thuộc / SAST | Không | ❌ |
| Mã hóa dữ liệu nghỉ, TLS | Chưa (môi trường local) | ❌ |
| Xóa/xuất dữ liệu cá nhân, retention | Không (chỉ soft-delete ở `Message`) | ❌ |

### 1.4. Chất lượng API & dữ liệu
| Năng lực | Hiện trạng | |
|---|---|---|
| Hợp đồng lỗi thống nhất | `GlobalExceptionHandler` trả `{code,message}` — ✅ nhất quán nhưng thiếu `traceId`/chi tiết trường | ⚠️ |
| Validation đầu vào | ✅ Bean Validation ở DTO chính; đã rà biên | ✅ |
| Tài liệu API (OpenAPI) | Không | ❌ |
| Phiên bản API (`/api/v1`) | Không | ❌ |
| Phân trang/lọc/sắp xếp | Chỉ `users` phân trang phía server; còn lại trả toàn bộ (phân trang phía client mới thêm) | ⚠️ |
| **Migration CSDL** | **Không Flyway**; `core-lms` dùng `ddl-auto: update` (không đổi/xóa được ràng buộc cũ — đã gây lỗi CHECK constraint 2 lần, phải vá bằng `SchemaPatcher`) | ❌ nghiêm trọng khi lên thật |
| N+1 / hiệu năng truy vấn | Nhiều vòng lặp gọi repo từng dòng (báo cáo, bảng điểm, lịch, tiến độ) — ổn với dữ liệu nhỏ, kém khi lớn | ⚠️ |
| Cache | Không (Redis chỉ cho rate limit) | ❌ |
| Replica đọc | Chỉ `core-lms`; `assessment` đọc primary | ⚠️ |
| Sao lưu / khôi phục | Không (không WAL archive, không kịch bản restore) | ❌ |

### 1.5. Chất lượng & vận hành
| Năng lực | Hiện trạng | |
|---|---|---|
| Kiểm thử tự động | **0 file test** (BE+FE). Có các script rà soát tạm ngoài repo | ❌ |
| CI/CD, Docker, IaC, môi trường | Không | ❌ |
| Runbook / hướng dẫn vận hành | Có `README/CLAUDE.md` chạy local; chưa có runbook sự cố | ⚠️ |
| Frontend: error boundary, retry GET, báo lỗi tập trung, a11y, i18n | Không error boundary; không retry/backoff có chủ đích; chuỗi tiếng Việt rải trong code; a11y cơ bản | ⚠️ |

**Tóm lại:** phần *nghiệp vụ và phân quyền* ở mức tốt; các nền tảng *độ tin cậy, quan sát, vận hành dữ liệu* gần như chưa có. Ba rủi ro lớn nhất nếu đưa lên môi trường thật: (1) **khóa JWT sinh mỗi lần khởi động**, (2) **không có migration**, (3) **gửi sự kiện trong transaction + consumer không DLQ**.

## 2. Kế hoạch (ưu tiên theo rủi ro × giá trị chấm điểm môn Cloud)

### Giai đoạn 1 — Chặn các rủi ro "chết người" (≈ 1 tuần)
1. **Khóa JWT bền vững:** nạp RSA key từ biến môi trường/secret (PEM), thêm `kid`, JWKS trả khóa hiện tại + khóa cũ để xoay khóa; token version/`password_changed_at` để khóa tài khoản & đổi mật khẩu vô hiệu token cũ (kiểm ở gateway bằng cache Redis ngắn hạn). *Nghiệm thu:* restart `core-lms` không làm đăng xuất ai; khóa tài khoản có hiệu lực ≤ 1 phút.
2. **Flyway:** baseline từ schema hiện tại (V1), chuyển `ddl-auto: validate`, bỏ `SchemaPatcher`; migration đi cùng mỗi thay đổi entity; CI kiểm migration trên DB trống. *Nghiệm thu:* DB mới dựng chỉ bằng Flyway; chạy lại trên DB cũ không lỗi.
3. **Transactional outbox:** ghi `OutboxEvent` cùng transaction; relay (scheduler + `FOR UPDATE SKIP LOCKED`) publish có publisher-confirm, đánh dấu `SENT`, retry có backoff, quá N lần → `FAILED`. Thay 9 chỗ `convertAndSend`. *Nghiệm thu:* tắt RabbitMQ → đăng thông báo/chấm bài vẫn thành công, bật lại thì thông báo đến đủ, không trùng.
4. **Consumer an toàn:** Spring Retry (backoff mũ, tối đa 5 lần) → **DLX + parking-lot queue**; trang/endpoint Admin xem & phát lại tin trong DLQ.
5. **Bí mật & cấu hình theo profile:** bỏ mật khẩu mặc định khỏi repo (đọc từ env, ứng dụng từ chối khởi động ở profile `prod` nếu còn giá trị mặc định), `application-{dev,prod}.yml`.

### Giai đoạn 2 — Idempotency, retry, timeout, rate limit (≈ 1 tuần)
1. **Idempotency-Key** cho API ghi quan trọng: ghi nhận học phí/hoàn tiền, ghi danh, bắt đầu/nộp quiz, nộp bài tập, tạo phúc khảo, hoàn thành khóa, thông báo hệ thống, ghi danh hàng loạt. Bảng `idempotency_keys(key,user,endpoint,request_hash,status,response,expires_at)`, interceptor phát lại cùng phản hồi, `409/422` khi cùng key khác nội dung. Frontend `apiClient` tự sinh key cho mọi `POST/PUT/PATCH`. *Nghiệm thu:* gửi cùng request 5 lần song song → đúng 1 bản ghi, 5 phản hồi giống nhau.
2. **Timeout & retry có chủ đích:** timeout kết nối/đọc cho S3 client (MinIO), JDBC (`socketTimeout`, `statement_timeout`), RabbitMQ; Resilience4j `Retry` + `CircuitBreaker` quanh gọi MinIO và publish; **Gateway:** `Retry` filter (chỉ GET/idempotent, backoff) + `CircuitBreaker` với phản hồi fallback thân thiện (503 + `Retry-After`).
3. **Rate limit theo chính sách:** đăng nhập/đăng ký/quên mật khẩu (theo IP + email, cửa sổ trượt), upload-url, nộp quiz; trả `429` + `Retry-After` + `X-RateLimit-*`; **khóa tạm tài khoản** sau 5 lần sai (leo thang), thông báo cho người dùng.
4. **ShedLock** cho 3 scheduler; **`@Version`** cho course/quiz/lesson/assignment (trả 409 kèm gợi ý tải lại); giới hạn kích thước request + upload (dung lượng, MIME, magic-bytes) phía server.
5. **Graceful shutdown + probes:** `server.shutdown=graceful`, liveness/readiness (kiểm DB/Redis/RabbitMQ/MinIO), tinh chỉnh Hikari.

### Giai đoạn 3 — Quan sát (≈ 1 tuần)
1. **Log JSON** (logback + encoder), `X-Request-Id`/`traceId` do gateway tạo → MDC → đính vào header RabbitMQ → worker; che PII (email, token); mức log theo profile; access log ở gateway.
2. **Metrics:** Micrometer + Prometheus (`/actuator/prometheus` chỉ nội bộ); metric nghiệp vụ (quiz bắt đầu/nộp, thời gian nộp, ghi danh, độ trễ hàng đợi, kích thước outbox/DLQ, bài chờ chấm); Grafana dashboard (RED + DB pool + RabbitMQ).
3. **Tracing:** Micrometer Tracing + OpenTelemetry → Tempo/Jaeger (gateway → service → RabbitMQ → worker).
4. **SLI/SLO + cảnh báo:** ví dụ nộp quiz p95 < 800 ms, lỗi < 0,5%, độ trễ thông báo < 10 s; quy tắc cảnh báo + runbook ngắn.
5. **Mở rộng audit:** đăng nhập thành công/thất bại, đổi mật khẩu, chấm/sửa điểm, phúc khảo, khóa/mở tài khoản.

### Giai đoạn 4 — API, dữ liệu, hiệu năng (≈ 1 tuần)
1. **OpenAPI/Swagger** (springdoc) + lỗi theo **problem+json** (`code`, `message`, `traceId`, `fieldErrors`); `/api/v1` (gateway rewrite để không phá frontend).
2. **Phân trang/lọc/sắp xếp phía server** cho mọi danh sách có thể lớn (thông báo, nhật ký, ghi danh, khóa học, bài nộp, tin nhắn).
3. **Tối ưu N+1:** viết truy vấn gộp cho báo cáo, bảng điểm, lịch, tiến độ; kiểm bằng `pg_stat_statements` + test đếm số câu lệnh; chỉ mục bổ sung theo kết quả đo.
4. **Cache-aside (Redis)** cho outline khóa học, danh mục, public overview, tiến độ; TTL + invalidate khi Admin sửa; ETag/`If-None-Match`; `assessment` đọc replica.
5. **Sao lưu:** `pg_dump` định kỳ + WAL archive, **kịch bản khôi phục có chạy thật** (đo RPO/RTO).

### Giai đoạn 5 — Kiểm thử, CI/CD, bảo mật chuỗi cung ứng (≈ 1–1,5 tuần, chạy song song)
1. **Test:** đơn vị (công thức điểm, quy tắc hoàn thành, máy trạng thái ghi danh), tích hợp **Testcontainers** (Postgres/RabbitMQ/MinIO) cho luồng lõi và đồng thời (ghi danh vượt sĩ số, nộp quiz đúp, outbox), hợp đồng API, **Playwright E2E** (chuyển các script rà soát đã có), **k6** tải quiz.
2. **CI (GitHub Actions):** build + test + lint/typecheck + kiểm Flyway + quét phụ thuộc (Dependabot/OWASP/Trivy) + SAST; **CD:** Dockerfile đa tầng, đẩy image, deploy, smoke test, rollback một lệnh.
3. **Security headers** (HSTS, CSP, nosniff, frame-ancestors, referrer-policy) ở gateway/host frontend; chính sách mật khẩu; HTTPS bắt buộc ở `prod`.
4. **Frontend:** error boundary, retry GET có backoff (react-query), trang lỗi/offline, gửi `Idempotency-Key`, báo lỗi tập trung, rà a11y (bàn phím, tương phản), gom chuỗi để dễ i18n.
5. **Dữ liệu cá nhân:** chính sách lưu trữ, xuất/xóa dữ liệu người dùng (ẩn danh hóa).

## 3. Thứ tự & liên hệ với yêu cầu môn học
1. **GĐ1** (rủi ro cao nhất, nền cho mọi thứ) → 2. **GĐ3** (quan sát để đo được các giai đoạn sau) → 3. **GĐ2** → 4. **GĐ5-CI/test** (chạy song song từ sớm) → 5. **GĐ4**.
Ánh xạ rubric (CLOUD_ALIGNMENT.md): GĐ1–2 = *failure handling, security, idempotency*; GĐ3 = *observability, SLI/SLO*; GĐ4 = *cache, replica, backup/RPO-RTO*; GĐ5 = *CI/CD, testing, supply-chain security*. Kết quả đo của GĐ2–4 chính là bằng chứng cho demo "tải quiz đồng thời" và "kịch bản lỗi".

## 4. Không làm (có chủ đích, cần ADR)
Kubernetes, service mesh, nhiều message broker, event sourcing/CQRS đầy đủ, tách CSDL theo service — quá mức so với quy mô MVP; ghi trade-off trong ADR.

---

## Implementation status (updated 2026-11-17)

Done and verified at runtime (tests / CI-CD intentionally deferred):

| Area | What exists now | Verified |
|---|---|---|
| JWT keys | persisted PEM (prod: mandatory file), kid thumbprint, previous keys in JWKS for rotation | restart keeps tokens valid |
| Token revocation | Redis `revoked-after`, checked at gateway; disable user / password change | disabled user → 401 `TOKEN_REVOKED`; old token 401 after password change |
| Schema | Flyway V1 baseline, V2 drop enum CHECKs, V3 outbox cols, V4 shedlock + `@Version` (Course, Quiz); Hibernate `validate`; SchemaPatcher removed | fresh DB + existing DB |
| Outbox | all notification publishers write to `outbox_events` in the business transaction; worker `OutboxRelay` (SKIP LOCKED, publisher confirms, backoff, FAILED after 10) | RabbitMQ stopped → API still 200, delivered after restart |
| Consumer | 5 retries with backoff, then DLX → `oep.worker.notification.dlq`; admin "Vận hành" page + replay/retry endpoints | poison messages dead-lettered, replay works |
| Secrets | `application-prod.yml` (no defaults), `ProdSecretsGuard`, `.env.example` | compiled |
| Idempotency | gateway `Idempotency-Key` (Redis, 24h replay, 409 in-progress, 422 reuse); frontend adds key per mutation | replay + 422 verified |
| Resilience | connect/response timeouts, Retry (GET) + CircuitBreaker + `/fallback` 503, per-route rate limits (auth by IP, quiz write, upload URLs) with `Retry-After`, login lockout (5 fails → 15 min, 429) + LOGIN_FAILED/ACCOUNT_LOCKED audit | lockout verified; breaker not load-tested |
| Ops | ShedLock on 3 schedulers, graceful shutdown, liveness/readiness probes, upload MIME block-list | probes verified |
| Observability | JSON logs under `prod` profile, traceId in dev logs, Micrometer + Prometheus on all 4 services, OTLP tracing (sampling `TRACE_SAMPLE_RATE`, default 0), Prometheus/Grafana/Jaeger compose profile `observability`, SLO alert rules, outbox/login business metrics | `/actuator/prometheus` on all services |
| API | springdoc OpenAPI (`/v3/api-docs`, `/swagger-ui.html`) on core-lms + assessment; `/api/v1/*` alias at gateway | verified |
| Backup | `scripts/backup.sh`, `scripts/restore.sh` (restore drill into a scratch DB) | drill passed |
| Security/UX | password policy (letter+digit, common-password list), security headers at gateway, React ErrorBoundary, offline banner, axios retry with backoff + `Retry-After` | verified (headers, policy) |

Added afterwards (branch `build/deploy-prep`, verified at runtime): server-side pagination of the audit log (`PageResponse`), Hibernate `default_batch_fetch_size` + dev-only `QueryCountFilter` (N+1 detector; fixed the public overview and the student catalog), Redis cache-aside (`CacheService`, public overview, 60 s, fails open), replica routing for assessment-service, optimistic-lock checks in Course/Quiz updates (`version` in request/response → 409 `STALE_VERSION`), RFC 7807 `application/problem+json` errors (legacy `code`/`message` kept), personal-data export (`GET /api/users/me/export`), admin anonymisation (`POST /api/admin/users/{id}/anonymize`) and a daily retention job (notifications 180 d, delivered outbox 7 d, expired refresh tokens 30 d; ShedLock).

Still open: extended audit coverage for non-admin writes, automated tests, CI/CD pipeline, load test / chaos evidence.
