# CLAUDE.md — Project 8: Online Education Platform (Backend)

Java 21, Spring Boot 3.3.5, Maven multi-module. Hạ tầng dev: PostgreSQL (1 primary + 1 read replica), Redis, RabbitMQ bằng Docker Compose.

## 1. Kiến trúc

### 1.1. Sơ đồ tổng thể

```
                          Client (React SPA)
        (1) Xem video          (2) Gọi API           (3) Direct upload
         (file tĩnh)         (REST / GraphQL)        (video / tài liệu)
              |                     |                       |
              v                     v                       |
     +----------------+   +----------------------+          |
     |   Cloud CDN    |   |  api-gateway :8080   |          |
     |  (Edge tier)   |   |  JWT check           |          |
     +-------+--------+   |  Redis rate limit    |          |
             |            |  path routing        |          |
             | cache miss +----------+-----------+          |
             |                       |                      |
             |         +-------------+--------------+       |
             |         |             |              |       |
             |         v             v              v       |
             |  +-------------+ +-------------+ +-----------+--+
             |  |core-lms-    | |assessment-  | | ai-tutor     |
             |  |service:8081 | |service:8082 | | (Python)     |
             |  |user, course,| |quiz, nộp    | | trích nguồn, |
             |  |enrollment,  | |bài, trạng   | | hỏi đáp RAG  |
             |  |auth, cấp    | |thái thi     | +------+-------+
             |  |Signed URL   | |(chịu tải    |        |
             |  +--+-------+--+ | cao)        |        v
             |     |       |    +--+-------+--+  +-----------+
             |     |       |       |       |     | Pinecone  |
             |     |       |       |       v     | (Vector   |
             |     |       |       |  +---------+|  DB)      |
             |     |       |       |  |  Redis  |+-----------+
             |     |       |       |  |  :6379  |
             |     |       |       |  |trạng thái thi,
             |     |       |       |  |hấp thụ tải|
             |     |       |       |  +----+-----+
             |     |       v       v       |
             |     |     +---------------+ |
             |     |     |   RabbitMQ    | |
             |     |     |  :5673 (Event | |
             |     |     |  Bus)         | |
             |     |     +-------+-------+ |
             |     |             | (5) consume task
             |     |             v         |
             |     |   +--------------------------+
             |     |   |  worker-service :8083    |<--+ đồng bộ điểm
             |     |   |  nén video (HLS 720p/    |     từ Redis xuống DB
             |     |   |  1080p), email, push,    |
             |     |   |  đồng bộ điểm thi        |
             |     |   +-----+--------------+-----+
             |     |         |              |
             |     |         | (6) lưu file | ghi nền
             |     |         |   đã xử lý   |
             v     v         v              |
      +--------------------------------+    |
      |     GCP Cloud Storage          |<---+--- (3) Direct upload
      |  video gốc, HLS, tài liệu      |        (qua Signed URL)
      +--------------------------------+
                                              |
                                              v
      +------------------------------------------------------+
      |           PostgreSQL :5432 (db: oep) - dùng chung    |
      |  +------------------------+                          |
      |  | Primary (Master)       |  chỉ Ghi: tạo user,      |
      |  | Write                  |  tạo khóa học, nộp bài   |
      |  +-----------+------------+                          |
      |              | Streaming Replication (liên tục)      |
      |              v                                       |
      |  +------------------------+                          |
      |  | Read Replica(s)  ... n |  chỉ Đọc: xem danh sách, |
      |  | Read-heavy             |  xem tiến độ học tập     |
      |  +-----------+------------+                          |
      +--------------|---------------------------------------+
                     | Archiving
                     v
            +------------------------+
            | Backup (GCP Archive)   |
            | WAL logs liên tục      |
            | Daily snapshots        |
            +------------------------+
```

- `core-model` là thư viện dùng chung (jar, không chạy độc lập): entity, enum, event, exception, cấu hình routing DataSource. Ba service Spring Boot cùng dùng.
- `api-gateway` chạy WebFlux và **không** phụ thuộc `core-model`.
- Frontend (SPA) ở repo khác, chỉ gọi qua `http://localhost:8080/api/...`.
- Đã làm: PostgreSQL 1 primary (ghi, `:5432`) + 1 read replica (đọc, `:5433`). Chỉ `core-lms-service` bật routing đọc/ghi (mục 5). Auth (JWT RS256 + JWKS) trong Core LMS: đăng ký/đăng nhập/refresh/logout/đổi mật khẩu, admin tạo giáo viên với mật khẩu random + bắt đổi lần đầu — chi tiết ở `DATABASE.md` mục 7.
- Chưa làm: AI Tutor (Python + Pinecone), Cloud Storage/CDN, nhiều replica, backup WAL sang GCP Archive.

## 2. Cấu trúc thư mục

```
backend/
├── CLAUDE.md
├── pom.xml                      project cha
├── docker-compose.yml           postgres (primary), postgres-replica, redis, rabbitmq (project name: oep)
├── docker/postgres/             primary-init.sh, replica-entrypoint.sh
├── core-model/                  THƯ VIỆN dùng chung
│   └── src/main/java/com/oep/coremodel/
│       ├── constant/            MessagingConstants
│       ├── datasource/          DbRole, ReadWriteRoutingDataSource, RoutingDataSourceConfig
│       ├── entity/              BaseEntity + 10 package theo miền (38 bảng, xem DATABASE.md):
│       │                        identity, course, enrollment, quiz, assignment,
│       │                        notification, system, social, registration, ai
│       ├── enums/               15 enum
│       ├── event/               QuizPublishedEvent, NotificationRequestedEvent, ExamScoreSyncEvent
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
| PostgreSQL primary, ghi (db, user, mật khẩu đều `oep`) | 5432 |
| PostgreSQL replica, chỉ đọc (cùng db, user, mật khẩu) | 5433 |
| Redis | 6379 |
| RabbitMQ AMQP / Management UI (user, mật khẩu `oep`) | 5673 / 15673 |

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
- Schema do **Flyway** quản lý (`core-lms-service/src/main/resources/db/migration`: V1 baseline, V2 bỏ CHECK enum, V3 cột outbox, V4 shedlock + `version`); Hibernate chỉ `validate`. Replica tự nhận qua replication. Assessment và Worker không migrate nên luôn chạy Core LMS trước. DB cũ tạo bằng `ddl-auto: update` được baseline tự động ở V1. Làm sạch DB: `docker compose down -v && docker compose up -d`, rồi chạy lại Core LMS. Thêm thay đổi schema = thêm file `V<n>__mo_ta.sql`, không sửa file cũ.
- Profile: mặc định `dev`; image Docker chạy `prod` (xem `DEPLOYMENT.md`). Chạy thử kiểu production: `docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build` (sinh secret bằng `scripts/gen-secrets.sh`).
- Mọi lệnh `docker compose` phải chạy trong thư mục có `docker-compose.yml`. Không dùng `--remove-orphans` nếu máy có container khác.

Kiểm tra: `curl http://localhost:8081/actuator/health` và `docker compose exec postgres psql -U oep -d oep -c '\dt'` (phải có 38 bảng, replica cũng vậy). Chi tiết từng bảng/cột/lý do thiết kế: xem `DATABASE.md`.

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
    - `assessment-service` và `worker-service` giữ trên primary.
- Kiểm tra: primary `select * from pg_stat_replication;` (phải `streaming`); replica `select pg_is_in_recovery();` (phải `t`).
- Lỗi thường gặp: xóa volume replica mà giữ primary thì xóa slot cũ: `select pg_drop_replication_slot('replica_1');`.