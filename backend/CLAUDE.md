# CLAUDE.md — Project 8: Online Education Platform (Backend)

> **Tài liệu nguồn (đồng bộ 27/11/2026):** Mọi yêu cầu nghiệp vụ, kiến trúc mục tiêu, trạng thái `[ĐÃ TRIỂN KHAI]` / `[KẾ HOẠCH BỔ SUNG - MVP]` và phương án Cloud/Media (`[TBD]`) xem tại [BRD v1.4](../docs/BRD_Project8_OEP_v1.4.md) và [BUSINESS.md](BUSINESS.md).

Java 21, Spring Boot 3.3.5, Maven multi-module. Hạ tầng dev: PostgreSQL 16 (1 primary + 1 read replica), `ai-postgres` (PostgreSQL 17 + `pgvector`, profile `ai-tutor`), Redis 7, RabbitMQ 3, MinIO bằng Docker Compose.

## 1. Kiến trúc

### 1.1. Sơ đồ tổng thể (phản ánh mã nguồn hiện tại & BRD v1.4)

```text
                          Client (React SPA — frontend/)
        (1) Xem video HLS      (2) Gọi API REST/SSE     (3) Direct upload
         (360p/720p)            (qua CloudFront/Nginx)   (Presigned URL)
              |                       |                         |
              v                       v                         |
     +------------------+   +----------------------+            |
     | Media Edge CDN   |   |  api-gateway :8080   |            |
     | [TBD: CloudFront |   |  JWT RS256/JWKS      |            |
     | OAC Signed Cookie|   |  Redis rate limit    |            |
     | / CF R2 Worker]  |   |  Revocation + CB     |            |
     +--------+---------+   +----------+-----------+            |
              |                        |                        |
              |          +-------------+--------------+         |
              |          |                            |         |
              |          v                            v         |
              |  +----------------+         +-----------------+ |
              |  |core-lms-       |         |assessment-      | |
              |  |service :8081   |         |service :8082    | |
              |  |auth, course,   |         |quiz, nộp bài,   | |
              |  |enroll, HLS URL,|         |chấm điểm,       | |
              |  |AI proxy (JWT)  |         |phúc khảo        | |
              |  +--+----+----+---+         +---+--------+----+ |
              |     |    |    |                 |        |      |
              |     |    |    | (Signed scope)  v        |      |
              |     |    |    +---------> +-----------+  |      |
              |     |    |                | ai-tutor  |  |      |
              |     |    |                | (Python/  |  |      |
              |     |    |                | FastAPI)  |  |      |
              |     |    |                +-----+-----+  |      |
              |     |    |                      |        |      |
              |     |    |                      v        |      |
              |     |    |            +------------------+      |
              |     |    |            | ai-postgres:5435 |      |
              |     |    |            | PG17 + pgvector  |      |
              |     |    |            | (ADR-0015)       |      |
              |     |    |            +------------------+      |
              |     |    |                               |      |
              |     |    v (Outbox)                      v      |
              |     |  +-----------------------------------+    |
              |     |  | RabbitMQ :5673 (Event Bus + DLQ)  |    |
              |     |  | + Redis :6379 (Cache/Rate/Lock)   |    |
              |     |  +-----------------+-----------------+    |
              |     |                    | consume task         |
              |     |                    v                      |
              |     |  +-----------------------------------+    |
              |     |  | worker-service :8083              |    |
              |     |  | FFmpeg HLS 360p/720p, in-app/SMTP |    |
              |     |  | notification (+ DLQ), score-sync  |    |
              |     |  +--------+------------------+-------+    |
              |     |           |                  |            |
              v     v           v                  |            |
       +------------------------------+            |            |
       | Object Storage               |<-----------+------------+
       | MinIO (dev) / AWS S3 (prod)  | (3) Direct upload
       | [TBD PA-B: Cloudflare R2]    |     (Presigned URL)
       +------------------------------+            |
                                                   v
       +------------------------------------------------------+
       |        PostgreSQL 16 :5432 (db: oep) - LMS chính     |
       |  +------------------------+                          |
       |  | Primary (Master) Write |                          |
       |  +-----------+------------+                          |
       |              | Streaming Replication                 |
       |              v                                       |
       |  +------------------------+                          |
       |  | Read Replica :5433 Read| (core-lms & assessment)  |
       |  +------------------------+                          |
       +------------------------------------------------------+
```

- `core-model` là thư viện dùng chung (jar, không chạy độc lập): entity, enum, event, exception, cấu hình routing DataSource, công thức `FinalGrade`. Ba service Spring Boot cùng dùng.
- `api-gateway` chạy WebFlux và **không** phụ thuộc `core-model`.
- `frontend/` (React SPA) nằm cùng repo, gọi API qua `http://localhost:8080/api/...` (hoặc qua Nginx/CloudFront ở prod).
- `ai-tutor/` (Python/FastAPI + 2 worker) nằm cùng repo, sở hữu DB `ai-postgres` (PG17 + `pgvector`) theo `ai-tutor/adr/0015-ai-relational-ownership.md` và giao tiếp với `core-lms-service` qua signed JWT assertion + RabbitMQ.
- **Đã triển khai:** PostgreSQL 16 Primary + Read Replica (routing đọc/ghi cho cả `core-lms-service` và `assessment-service`), Flyway `V1–V6`, JWT RS256 + JWKS xoay khóa + Redis token revocation, MinIO/AWS S3 presigned URL, FFmpeg HLS `360p/720p`, Transactional Outbox + RabbitMQ + DLQ, Terraform AWS (`infra/aws/ec2-demo`, `ecs-managed`), `ai-tutor` Document RAG 4 định dạng (`TEXT/PDF/MD/JSON`).
- **Kế hoạch bổ sung MVP theo BRD v1.4:** xem chi tiết trong [BRD v1.4](../docs/BRD_Project8_OEP_v1.4.md) và [STATUS.md](STATUS.md) (gồm Email verify, Root Admin, LO tùy chọn, Access Period/Archived + 2 rào chắn, 4 loại Quiz + Mass Exam 500 CCU Redis/Queue, Rubric + Claim 30p, nâng cấp AI Tutor Socratic/Timestamp/Mistake Cache/Eval P5, đưa `ai-tutor` vào `docker-compose.prod.yml`/Terraform, Edge HLS Signed Cookies).

## 2. Cấu trúc thư mục

```
backend/
├── CLAUDE.md
├── pom.xml                      project cha
├── docker-compose.yml           postgres (primary), postgres-replica, redis, rabbitmq, minio; profile ai-tutor (ai-postgres), observability (prometheus/grafana/jaeger)
├── docker/postgres/             primary-init.sh, replica-entrypoint.sh
├── core-model/                  THƯ VIỆN dùng chung
│   └── src/main/java/com/oep/coremodel/
│       ├── constant/            MessagingConstants
│       ├── datasource/          DbRole, ReadWriteRoutingDataSource, RoutingDataSourceConfig
│       ├── entity/              BaseEntity + 9 package theo miền (46 bảng LMS + 3 bảng legacy; xem ADR-0015):
│       │                        identity, course, enrollment, quiz, assignment,
│       │                        notification, system, social, registration
│       ├── enums/               19 enum
│       ├── event/               8 event (QuizPublishedEvent, NotificationRequestedEvent, ExamScoreSyncEvent, VideoTranscodeRequestedEvent, AiIndexRequestedEvent, ...)
│       └── exception/           BusinessException, ErrorCode
├── api-gateway/                 :8080  config/ (SecurityConfig, RateLimitConfig)
├── core-lms-service/            :8081  config/, repository/, service/
├── assessment-service/          :8082  config/
└── worker-service/              :8083  config/, listener/
```

Package gốc `com.oep`. Quy ước: entity chỉ sửa trong `core-model`; mỗi service chỉ viết repository và service của riêng mình.

## 3. Cách chạy

Yêu cầu: JDK 21, Maven 3.9+, Docker. Trong IntelliJ cài plugin **Lombok** và bật *Annotation Processing*.

| Thành phần | Cổng |
|---|---|
| api-gateway / core-lms / assessment / worker | 8080 / 8081 / 8082 / 8083 |
| PostgreSQL 16 primary (ghi) / replica (đọc) (db `oep`) | 5432 / 5433 |
| PostgreSQL 17 + `pgvector` (`ai-postgres`, profile `ai-tutor`, db `oep_ai`) | 5435 |
| Redis | 6379 |
| RabbitMQ AMQP / Management UI (user, mật khẩu `oep`) | 5673 / 15673 |
| MinIO S3 API / Console (dev) | 9000 / 9001 |

```bash
cd backend                                         # thư mục có docker-compose.yml

docker compose up -d                               # 1) hạ tầng
mvn -pl core-model -am install -DskipTests         # 2) cài core-model vào ~/.m2
mvn -pl core-lms-service spring-boot:run           # 3) chạy Core LMS TRƯỚC (tạo bảng)

# 4) tùy chọn, mỗi lệnh một terminal
mvn -pl assessment-service spring-boot:run
mvn -pl worker-service spring-boot:run
mvn -pl api-gateway spring-boot:run
```

Lưu ý:
- **Không** dùng `-am` với `spring-boot:run` (lỗi "Unable to find a suitable main class").
- Sửa `core-model` xong phải chạy lại bước 2. Chạy bằng IntelliJ (mở từ `pom.xml` gốc, bấm Run ở class `*Application`) thì không cần bước 2.
- Schema do **Flyway** quản lý (`core-lms-service/src/main/resources/db/migration`: `V1` baseline 48 bảng, `V2` bỏ CHECK enum, `V3` cột outbox, `V4` shedlock + `version`, `V5` video transcode `transcode_status`/`hls_prefix`, `V6` BRD alignment); Hibernate chỉ `validate`. Replica tự nhận qua replication. Assessment và Worker không migrate nên luôn chạy Core LMS trước. DB cũ tạo bằng `ddl-auto: update` được baseline tự động ở V1. Làm sạch DB: `docker compose down -v && docker compose up -d`, rồi chạy lại Core LMS. Thêm thay đổi schema = thêm file `V<n>__mo_ta.sql`, không sửa file cũ.
- Profile: mặc định `dev`; image Docker chạy `prod` (xem `DEPLOYMENT.md`). Chạy thử kiểu production: `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build` (sinh secret bằng `scripts/gen-secrets.sh`).
- Mọi lệnh `docker compose` phải chạy trong thư mục có `docker-compose.yml`. Không dùng `--remove-orphans` nếu máy có container khác.

Kiểm tra: `curl http://localhost:8081/actuator/health` và `docker compose exec postgres psql -U oep -d oep -c '\dt'` (49 bảng Flyway + `flyway_schema_history`, replica cũng vậy). Chi tiết từng bảng/cột/lý do thiết kế: xem `DATABASE.md` và `../docs/BRD_Project8_OEP_v1.4.md`.

## 4. Nhánh Git

Hai nhánh chính, đều được bảo vệ (không push trực tiếp, chỉ merge bằng Pull Request):

| Nhánh | Vai trò |
|---|---|
| `dev` | Nhánh tích hợp, mọi tính năng mới gộp vào đây. Là nhánh mặc định để phát triển |
| `prod` | Nhánh ổn định, chỉ chứa code đã kiểm tra, dùng để triển khai |

Quy trình:
1. cứ checkout nhánh từ dev rồi merge vào dev ae nhá

Nhánh làm việc hiện tại: `dev-2` (tính năng) và `build/deploy-prep` (cấu hình dev/prod, Docker, Terraform AWS). Cấu hình `prod` đã sẵn sàng: Flyway + `ddl-auto: validate`, mọi secret/địa chỉ qua biến môi trường (danh sách đầy đủ ở `DEPLOYMENT.md` mục 4 và `.env.example`), thiếu biến hoặc dùng secret mặc định thì service không khởi động.

## 5. PostgreSQL Primary / Read Replica

- Primary `:5432` nhận ghi, replica `:5433` chỉ đọc, đồng bộ bằng streaming replication (bất đồng bộ). Hai bên phải cùng major version (hiện `16.14`).
- Dựng bằng `docker/postgres/primary-init.sh` (tạo role `replicator`, mở `pg_hba`) và `replica-entrypoint.sh` (`pg_basebackup -R`, chạy standby). Script init chỉ chạy khi volume primary còn trống.
- Spring: `RoutingDataSourceConfig` (trong `core-model`) chọn replica nếu transaction là `readOnly`, ngược lại chọn primary. Bật bằng `oep.datasource.routing-enabled=true`, cấu hình `oep.datasource.primary/replica.jdbc-url` (không dùng `spring.datasource`). `CoreLmsApplication` phải có `@Import(RoutingDataSourceConfig.class)`.
- Quy tắc code:
    - Đọc: `@Transactional(readOnly = true)` (replica). Ghi: `@Transactional` (primary).
    - Method repository mặc định `readOnly`, nên luôn gắn `@Transactional` rõ ràng cho method service.
    - Đọc ngay sau khi ghi thì dùng `@Transactional` thường (primary), vì replica có độ trễ nhỏ.
    - `core-lms-service` và `assessment-service` đều bật routing đọc/ghi; `worker-service` giữ trên primary.
- Kiểm tra: primary `select * from pg_stat_replication;` (phải `streaming`); replica `select pg_is_in_recovery();` (phải `t`).
- Lỗi thường gặp: xóa volume replica mà giữ primary thì xóa slot cũ: `select pg_drop_replication_slot('replica_1');`.