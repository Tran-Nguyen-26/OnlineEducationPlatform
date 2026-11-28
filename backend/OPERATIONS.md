# OPERATIONS.md — Sổ tay vận hành

Dành cho người chạy hệ thống (local prod-like hoặc AWS). Cấu hình/triển khai xem `DEPLOYMENT.md`. Quy chuẩn nghiệp vụ & SLO mục tiêu xem [BRD v1.4](../docs/BRD_Project8_OEP_v1.4.md).

## 1. Cổng và endpoint vận hành

| Thành phần | Dev | Prod (container) |
|---|---|---|
| Ứng dụng (nginx → gateway) | 5173 (Vite) → 8080 | 8088 (local) / 80 sau CloudFront |
| Health / readiness | `:808x/actuator/health` | **`:9090`** `/actuator/health/{liveness,readiness}` (chỉ trong mạng nội bộ) |
| Metrics Prometheus | `:808x/actuator/prometheus` | `:9090/actuator/prometheus` |
| OpenAPI / Swagger | `:8081`, `:8082` `/v3/api-docs`, `/swagger-ui.html` | tắt |
| RabbitMQ UI | 15673 | 127.0.0.1:15674 |
| Grafana / Prometheus / Jaeger | `--profile observability` | cùng profile: 3000 / 9091 / 16686 |

API công khai có thêm tiền tố phiên bản: `/api/v1/...` ≡ `/api/...` (gateway viết lại đường dẫn).

## 2. Đường truyền thông báo (outbox → RabbitMQ → worker)

1. Service ghi `outbox_events` **trong cùng transaction** với thay đổi nghiệp vụ (RabbitMQ chết không làm hỏng nghiệp vụ).
2. `worker-service` (`OutboxRelay`, mỗi 1 s, `FOR UPDATE SKIP LOCKED`) gửi lên RabbitMQ có publisher confirm; lỗi → thử lại với backoff, sau 10 lần → `FAILED`.
3. Consumer in-app thử 5 lần; hỏng nữa → hàng đợi chết `oep.worker.notification.dlq` *(riêng kênh gửi Email SMTP sẽ được nâng cấp theo BRD v1.4 để không nuốt lỗi và thực hiện Retry 3 lần + DLQ; hàng đợi AI Tutor cũng có DLQ riêng `oep.ai-tutor.index.dlq`)*.

Theo dõi và xử lý: **Admin → Vận hành** (`/admin/ops`, tự làm mới 5 s).

| Việc | Cách làm |
|---|---|
| Xem tồn đọng | `GET /api/admin/ops/status` → outbox pending/failed, độ tuổi cũ nhất, độ sâu hàng đợi, DLQ |
| Gửi lại outbox lỗi | nút "Gửi lại" hoặc `POST /api/admin/ops/outbox/retry-failed` |
| Phát lại DLQ | nút "Phát lại" hoặc `POST /api/admin/ops/dlq/replay?max=200` (sau khi đã sửa nguyên nhân) |
| Cảnh báo | `monitoring/alerts.yml`: `OutboxBacklog` (>100 trong 5 phút), `DeadLetterQueueNotEmpty` |

## 3. Sao lưu và phục hồi

```bash
scripts/backup.sh ./backups                         # pg_dump nén, giữ 14 bản mới nhất
scripts/restore.sh backups/oep-<time>.sql.gz         # khôi phục vào DB mới oep_restore để kiểm tra (restore drill)
```
Nên diễn tập khôi phục trước buổi demo. Trên AWS dùng thêm snapshot RDS tự động (7 ngày, đã bật trong Terraform).

## 4. Khóa JWT và thu hồi token

- Khóa ký RS256: `JWT_PRIVATE_KEY_FILE` (hoặc `JWT_PRIVATE_KEY_PEM` từ secret manager). `kid` = thumbprint, JWKS tại `/.well-known/jwks.json` (nội bộ).
- **Xoay khóa:** tạo khóa mới → đặt làm `JWT_PRIVATE_KEY_FILE`, đưa **public key cũ** vào `JWT_PREVIOUS_PUBLIC_KEY_FILES` trong ≥15 phút (TTL access token) → gỡ khóa cũ.
- **Thu hồi:** vô hiệu hóa user, đổi/đặt lại mật khẩu, ẩn danh → Redis `oep:revoked-after:{userId}`; gateway trả 401 `TOKEN_REVOKED` ngay lập tức.
- **Khóa đăng nhập:** 5 lần sai liên tiếp → khóa 15 phút (429 `TOO_MANY_ATTEMPTS`), ghi `LOGIN_FAILED` / `ACCOUNT_LOCKED` vào nhật ký. Mở khóa sớm: xóa key `oep:login-lock:<email>` trong Redis.

## 5. Giới hạn tốc độ và idempotency (gateway)

| Nhóm | Giới hạn |
|---|---|
| Mặc định (mỗi route chính) | 20 req/s/người dùng (burst 40); người chưa đăng nhập tính theo IP **thật** của máy khách |
| `/api/auth/login,register,forgot-password,reset-password,refresh` | 5 req/s/IP, burst 60 (`AUTH_RATE_REPLENISH`, `AUTH_RATE_BURST`) — đủ cho cả lớp sau một NAT; chống dò mật khẩu bằng khóa tài khoản |
| Nộp quiz/bài tập | 5 req/s (burst 15) |
| Xin URL upload | 2 req/s (burst 10) |

429 luôn kèm `Retry-After`. Mọi `POST/PUT/PATCH` có header `Idempotency-Key` (frontend tự gửi): lặp lại → phản hồi đầu tiên + `Idempotent-Replay: true`; cùng key khác nội dung → 422; đang xử lý → 409. Lưu 24 h trong Redis.

## 6. Dữ liệu cá nhân và lưu giữ

| Chức năng | Endpoint / nơi |
|---|---|
| Người dùng tải dữ liệu của mình | `GET /api/users/me/export` (nút trong trang Hồ sơ) |
| Admin ẩn danh tài khoản (không hoàn tác; giữ điểm/ghi danh; cấm áp dụng với Root Admin `is_root_admin=true` theo BRD v1.4) | `POST /api/admin/users/{id}/anonymize` (nút ở chi tiết người dùng) |
| Job lưu giữ dữ liệu 03:00 hằng ngày | `RetentionScheduler`: thông báo >180 ngày, outbox đã gửi >7 ngày, refresh token hết hạn >30 ngày (`oep.retention.*`), bản ghi đăng nhập *thành công* >90 ngày (`oep.retention.login-audit-days`). Không đụng điểm, ghi danh, chứng chỉ, và phần còn lại của nhật ký. *(BRD v1.4 `[KẾ HOẠCH BỔ SUNG - MVP]`: thêm luồng học viên tự yêu cầu khóa/xóa tài khoản và Worker Dormant 24 tháng xóa/ẩn danh hóa Heartbeat logs theo NĐ 356/2025/NĐ-CP.)* |

## 6b. Nhật ký hành động (audit)

Ngoài thao tác admin, hệ thống ghi vào `audit_logs` (cùng transaction với thay đổi): đăng nhập thành công/sai/khóa tài khoản, đổi mật khẩu, học viên ghi danh/vào danh sách chờ/rút khóa, nộp quiz, nộp/nộp lại bài tập, chấm và chấm lại bài tập, chấm câu tự luận, điều chỉnh điểm quiz, gửi và xử lý phúc khảo, đăng thông báo khóa học, ẩn danh tài khoản, thao tác Vận hành. Xem ở Admin → Nhật ký (phân trang phía server).

## 7. Chạy nhiều bản (scale-out)

Các job định kỳ dùng ShedLock (bảng `shedlock`) nên chỉ một bản chạy; relay outbox dùng `SKIP LOCKED` nên chạy song song an toàn. `@Version` trên Course/Quiz: sửa đồng thời trả 409 `STALE_VERSION`.

## 7b. Mở rộng số bản

`CORE_LMS_REPLICAS=2 docker compose -f docker-compose.prod.yml --env-file .env.prod up -d` chạy 2 bản core-lms; gateway tự chia tải theo DNS (round-robin, làm mới ~vài giây). Ngân sách kết nối DB = số bản × (pool primary tối đa 30 + pool replica tối đa 20): giữ nhỏ hơn `max_connections` của Postgres (local 300; RDS: chọn lớp máy đủ lớn hoặc dùng RDS Proxy). Lần đầu trên DB trống với profile `demo`, hãy bật **một** bản trước rồi mới scale (seeder không chống chạy song song).

## 8. Xử lý sự cố thường gặp

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| Service prod không khởi động, báo "Refusing to start ... default secrets" | `ProdSecretsGuard`: đặt mật khẩu thật (≥8 ký tự, không phải giá trị mặc định) |
| `oep.jwt.private-key-file is required under profile 'prod'` | thiếu `JWT_PRIVATE_KEY_FILE`/`JWT_PRIVATE_KEY_PEM` |
| 429 liên tục khi test bằng script | giới hạn `5 req/s/IP`, burst `60` trên `/api/auth/*` (`AUTH_RATE_REPLENISH`/`AUTH_RATE_BURST`, xem mục 5); tôn trọng header `Retry-After` hoặc thêm giãn cách giữa các lần đăng nhập |
| Thông báo không tới | Admin → Vận hành: outbox pending tăng ⇒ RabbitMQ/relay; DLQ có tin ⇒ lỗi consumer |
| 503 `SERVICE_UNAVAILABLE` + `Retry-After` | circuit breaker đang mở vì upstream lỗi; tự đóng lại sau ~10 s |
| Upload file bị chặn trên trình duyệt | CORS của bucket (`STORAGE_CORS_ORIGINS`) hoặc CSP `STORAGE_PUBLIC_ORIGIN` sai |
| Cổng 9000/15672 bị chiếm khi dựng stack | dừng stack dev (`docker compose stop`) hoặc đổi `RABBIT_UI_PORT` |
| Host không truy cập được container dù cổng đã publish | VPN trùng dải mạng Docker; compose cố định mạng `10.231.0.0/24`, đổi bằng `OEP_SUBNET` |
| Ổ NTFS báo `No space left` (hết MFT record) | dùng ext4 cho cache/build, `git gc`, tránh tạo nhiều file nhỏ |
