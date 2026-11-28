# Deployment guide (profiles, local production-like run, AWS)

## 1. Profiles

| | `dev` (default) | `prod` |
|---|---|---|
| Activated by | nothing (running from the IDE / `mvn spring-boot:run`) | `SPRING_PROFILES_ACTIVE=prod` (set in every Docker image) |
| Config | `application.yml` holds localhost defaults, `application-dev.yml` adds dev switches | `application-prod.yml`: **no defaults for secrets**, every value comes from the environment |
| Secrets | dev defaults (`oep/oep`, `Admin@12345`) | missing variable ⇒ startup fails; `ProdSecretsGuard` also rejects known default values |
| JWT key | generated once under `~/.oep/jwt` | mandatory PEM file (`JWT_PRIVATE_KEY_FILE`), mounted from a secret store |
| Swagger / OpenAPI | on | off |
| Actuator | same port as the API | **separate internal port 9090** (health, readiness, Prometheus); never routed by the gateway/ALB |
| Logs | readable console + traceId | one JSON object per line (CloudWatch / ELK / Loki) |
| Tracing sample | 100 % | `TRACE_SAMPLE_RATE` (default 10 %) |
| Storage | MinIO, bucket auto-created | MinIO **or** AWS S3 (empty endpoint ⇒ S3 + IAM role) |

## 2. Run the production-like stack locally

```bash
cd backend
scripts/gen-secrets.sh                                            # .env.prod + secrets/jwt-private-pkcs8.pem (git-ignored)
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
scripts/smoke.sh                                                  # end-to-end checks
# optional metrics/tracing:  docker compose -f docker-compose.prod.yml --env-file .env.prod --profile observability up -d
```

Open <http://localhost:8088>. Only nginx (8088) and MinIO (9000, needed by the browser for presigned URLs) are published;
everything else stays on the internal Docker network. The first admin login is printed by `gen-secrets.sh`.

Architecture of the stack: `browser → nginx (SPA + /api proxy) → api-gateway → core-lms / assessment → Postgres / Redis / RabbitMQ / S3`,
and `worker-service` relays the outbox to RabbitMQ and persists notifications.

### Dữ liệu demo (profile `demo`)

`DemoDataSeeder` tạo (một lần, bỏ qua nếu đã có): 2 giáo viên, 8 học viên, 3 môn, 3 khóa đã xuất bản (Tiếng Anh giao tiếp, Python nhập môn — học tuần tự, có học phí 500.000đ —, Kỹ năng thuyết trình), mỗi khóa 4 bài học, 1 quiz, 1 bài tập, 15 lượt ghi danh có tiến độ khác nhau, 13 kết quả quiz, 6 bài tập đã chấm, thông báo khóa học.
Bật bằng `SPRING_PROFILES_ACTIVE=prod,demo` (compose: `CORE_PROFILES=prod,demo` trong `.env.prod`, `gen-secrets.sh` đặt sẵn; Terraform: `seed_demo_data=true`, mặc định bật). Tắt bằng cách bỏ `demo`.

| Tài khoản | Mật khẩu |
|---|---|
| `gv.lan@oep.demo` (giáo viên: Tiếng Anh, Thuyết trình), `gv.minh@oep.demo` (Python) | `DEMO_PASSWORD` trong `.env.prod` (Terraform: `terraform output -raw demo_password`) |
| `hs01@oep.demo` … `hs08@oep.demo` (học viên) | như trên |
| `admin@oep.local` | mật khẩu admin (đổi lần đầu) |

Mã mời để học viên mới thử ghi danh: `DEMO-ENGLISH`, `DEMO-PYTHON`, `DEMO-PRESENT`. Tài khoản demo không bị buộc đổi mật khẩu để buổi trình bày trơn tru — **không bật `demo` cho hệ thống thật**.

## 2b. CI/CD (GitHub Actions)

| Workflow | Khi nào chạy | Làm gì |
|---|---|---|
| `.github/workflows/ci.yml` | mỗi push / pull request | `mvn verify` (unit test + `MigrationsTest` dựng Postgres bằng Testcontainers + test ffmpeg), `tsc` + `vite build`, build 5 image Docker, `docker compose config`, `terraform fmt/validate` cho cả hai stack |
| `deploy.yml` | thủ công (`workflow_dispatch`) | build 5 image, đẩy lên ECR (tag = commit SHA và `latest`), `ecs update-service --force-new-deployment`, đợi ổn định, smoke test qua CloudFront; nếu bản mới lỗi, ECS circuit breaker tự rollback |
| `rollback.yml` | thủ công | gắn lại `latest` vào một SHA cũ rồi triển khai lại |

Thiết lập một lần: tạo vai trò IAM cho GitHub OIDC có quyền ECR + ECS, lưu `AWS_ROLE_ARN` (secret) và `AWS_REGION` (variable) trong repository, tạo environment trùng tên stack (`demo`). Chưa chạy được trên GitHub từ máy này — đã kiểm tra cú pháp bằng `actionlint`.
Chạy test ở máy cục bộ: `mvn verify` (test Testcontainers tự bỏ qua nếu không có Docker; Docker Engine 29 cần `api.version=1.44`, đã cấu hình trong pom).

## 2c. Video HLS

Khi **Admin** xác nhận tải video lên (`complete-upload` — chỉ Admin có quyền biên soạn/tải học liệu theo [BRD v1.4](../docs/BRD_Project8_OEP_v1.4.md) và [BUSINESS.md](BUSINESS.md)), `core-lms-service` ghi một sự kiện `video.transcode` vào outbox; `worker-service` (image có ffmpeg, mỗi lần một video) tải file gốc, tạo 2 độ phân giải (720p và 360p, không phóng to quá nguồn; đoạn 6 giây) và tải lên `hls/<contentId>/`. Trạng thái nằm ở cột `lesson_contents.transcode_status` (`PENDING → PROCESSING → READY | FAILED`); admin xem huy hiệu "HLS" trong tab nội dung và bấm "Thử lại" khi lỗi (`POST /api/admin/lesson-contents/{id}/transcode`).
Phát: `GET /api/lesson-contents/{id}/view-url` trả thêm `hlsUrl`. Trình phát (hls.js) lấy playlist từ API (cần token, cùng quy tắc truy cập như file gốc); mỗi segment trong playlist là URL ký sẵn tới S3/MinIO (TTL 600s) nên video không đi qua server ứng dụng. Video chưa chuyển mã xong hoặc lỗi vẫn phát file gốc. Nâng cấp bảo mật HLS tại Edge CDN (cùng Dynamic Watermark) đang nghiên cứu 2 phương án `[TBD]` trong [BRD v1.4 §3.1 #4 & §7.2](../docs/BRD_Project8_OEP_v1.4.md): **PA-A** (AWS CloudFront OAC + Signed Cookies) vs. **PA-B** (Hybrid AWS Compute + Cloudflare R2 \$0 Egress + Worker HMAC).
Tài nguyên: worker cần ~1,5 GB RAM (Fargate: 1 vCPU / 2 GB), image worker ~1,2 GB do có ffmpeg.

## 3. AWS target

Terraform for both options lives in `../infra/aws/` (`ec2-demo`, `ecs-managed`; see its README for commands, cost and limits). The options below describe what those stacks create. *(Lưu ý BRD v1.4 `[KẾ HOẠCH BỔ SUNG - MVP]`: cần bổ sung `ai-tutor` + `ai-postgres` vào `docker-compose.prod.yml`, Terraform & CI/CD, cùng Scheduled Pre-warming 15 phút trước ca thi và Observability AWS.)*

### Option A — single EC2 with Docker Compose (cheapest, good for a demo)

| Local | AWS |
|---|---|
| Docker host | 1× EC2 `c7i-flex.large` (2 vCPU / 4 GB + 2 GB swap, theo `infra/aws/ec2-demo`), Ubuntu 24.04, 30 GB gp3, Docker + compose plugin |
| MinIO | **S3 bucket** (remove the `minio` service; leave `STORAGE_ENDPOINT` unset) |
| Postgres container | container (demo) or **RDS PostgreSQL** `db.t4g.micro` (set `DB_URL`) |
| nginx container | same, plus **TLS**: ALB + ACM certificate, or Caddy/Certbot on the instance |
| `.env.prod` secrets | AWS Secrets Manager / SSM Parameter Store, written to `.env.prod` by the user-data script |

Steps:
1. Create the S3 bucket (block public access ON). CORS is applied by the app when `STORAGE_CORS_ORIGINS=https://your-domain` is set
   (or set it in the console: allow `GET, PUT, HEAD`, headers `*`, origin = your site).
2. IAM role for the instance with `s3:GetObject, s3:PutObject, s3:DeleteObject, s3:ListBucket, s3:GetBucketCors, s3:PutBucketCors` on the bucket → no access keys needed.
3. Security group: inbound 443 (and 80 for redirect) only; **no** 5432/5672/6379/8080-8083/9090 from the internet.
4. On the instance: `git clone`, `scripts/gen-secrets.sh`, then edit `.env.prod`:
   `PUBLIC_ORIGIN=https://your-domain`, `STORAGE_PUBLIC_ORIGIN=https://<bucket>.s3.<region>.amazonaws.com`, and in the compose file
   delete `STORAGE_ENDPOINT`/keys, set `STORAGE_AUTO_CREATE_BUCKET=false`, `AWS_REGION=<region>`.
5. `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build`.

### Option B — managed services (closest to the architecture in `CLOUD_ALIGNMENT.md`)

| Component | AWS service |
|---|---|
| 5 containers | ECR + **ECS Fargate** (one task per service; 0.5 vCPU / 1 GB), target-group health check `GET :9090/actuator/health/readiness` |
| Postgres + read replica | **RDS PostgreSQL** Multi-AZ + read replica → `DB_URL` (writer) / `DB_REPLICA_URL` (replica) |
| Redis | **ElastiCache Redis** (set `REDIS_HOST`, `REDIS_PASSWORD`) |
| RabbitMQ | **Amazon MQ for RabbitMQ** (AMQPS 5671; `RABBIT_PORT=5671`) |
| Files / video | **S3** + **CloudFront** (CDN requirement of the course project) |
| Front door | **ALB** (ACM TLS) → nginx task; WAF optional |
| Secrets | **Secrets Manager** injected as task environment variables; JWT key as a secret mounted to `/run/secrets/jwt_private` |
| Logs / metrics / traces | CloudWatch Logs (JSON), Amazon Managed Prometheus/Grafana (scrape `:9090`), X-Ray or OTLP collector (`OTLP_ENDPOINT`) |
| Autoscaling | ECS target tracking on CPU; scheduled jobs are safe with several tasks (ShedLock), the outbox relay uses `FOR UPDATE SKIP LOCKED` |

## 4. Environment variables (prod)

| Variable | Used by | Meaning |
|---|---|---|
| `DB_URL` `DB_USER` `DB_PASSWORD` | core-lms, assessment, worker | primary database |
| `DB_REPLICA_URL` `DB_REPLICA_USER` `DB_REPLICA_PASSWORD` | core-lms, assessment | read replica (may equal the primary) |
| `REDIS_HOST` `REDIS_PASSWORD` | core-lms, gateway | token revocation, login lockout, rate limits, idempotency |
| `RABBIT_HOST` `RABBIT_PORT` `RABBIT_USER` `RABBIT_PASSWORD` | core-lms, assessment, worker | broker |
| `OEP_MAIL_ENABLED` `SPRING_MAIL_*` | worker | optional SMTP email delivery (default disabled; BRD v1.4 upgrades to Retry 3x + DLQ) |
| `JWT_PRIVATE_KEY_FILE` `JWT_PREVIOUS_PUBLIC_KEY_FILES` | core-lms | signing key / keys still valid during rotation |
| `JWK_SET_URI` | gateway, assessment | where to fetch public keys |
| `ADMIN_SEED_EMAIL` `ADMIN_SEED_PASSWORD` | core-lms | first admin (forced password change) |
| `STORAGE_BUCKET` `AWS_REGION` | core-lms, assessment | S3 bucket / region |
| `STORAGE_ENDPOINT` `STORAGE_PUBLIC_ENDPOINT` `STORAGE_ACCESS_KEY` `STORAGE_SECRET_KEY` | core-lms, assessment | **only** for MinIO; leave empty for AWS S3 |
| `STORAGE_CORS_ORIGINS` `STORAGE_AUTO_CREATE_BUCKET` | core-lms | apply bucket CORS on start / create the bucket |
| `CORS_ALLOWED_ORIGINS` `TRUSTED_PROXY_HOPS` | gateway | allowed origins; number of proxies in front (ALB=1, ALB+nginx=2) |
| `TRACE_SAMPLE_RATE` `OTLP_ENDPOINT` | all | tracing |

Key rotation: generate a new PEM, set it as `JWT_PRIVATE_KEY_FILE`, put the old **public** key in `JWT_PREVIOUS_PUBLIC_KEY_FILES`
for 15 minutes (access-token lifetime), then remove it.

## 5. Pre-demo checklist

- [ ] `scripts/smoke.sh` passes against the deployed URL
- [ ] HTTPS everywhere, HSTS reachable, no internal port open in the security group
- [ ] Admin password changed, no default credentials left (`ProdSecretsGuard` enforces this at start-up)
- [ ] S3 CORS set; upload a video and a submission file from the UI
- [ ] `scripts/backup.sh` run once and restore drill done (`scripts/restore.sh`)
- [ ] Admin → Vận hành shows outbox 0 failed / DLQ 0
- [ ] Remember to stop/destroy the AWS resources after the demo (RDS, NAT gateway and ALB bill hourly)
