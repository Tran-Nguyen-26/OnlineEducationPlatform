# CLAUDE.md — Project 8: Online Education Platform (Backend)

Java 21, Spring Boot 3.3.5, Maven multi-module. Hạ tầng dev: PostgreSQL, Redis, RabbitMQ bằng Docker Compose.

## 1. Kiến trúc

```
                 Client (SPA)
                      |
                      v
           +----------------------+
           |  api-gateway :8080   |  JWT check, Redis rate limit, path routing
           +----------+-----------+
                      |
        +-------------+--------------+
        v                            v
+------------------+        +--------------------+
| core-lms-service |        | assessment-service |
|      :8081       |        |       :8082        |
| user, course,    |        | quiz, nộp bài,     |
| enrollment, auth |        | trạng thái thi     |
+----+--------+----+        +---+------------+---+
     |        |                 |            |
     |        +--------+  +-----+            v
     |                 v  v             +-----------+
     |           +-------------+        |   Redis   |
     |           |  RabbitMQ   |        |   :6379   |
     |           |   :5673     |        +-----------+
     |           +------+------+
     |                  v
     |           +------------------+
     |           |  worker-service  |  :8083, thông báo + đồng bộ điểm
     |           +--------+---------+
     v                    v
+--------------------------------------+
|      PostgreSQL :5432 (db: oep)      |  dùng chung
+--------------------------------------+
```

- `core-model` là thư viện dùng chung (jar, không chạy độc lập): entity, enum, event, exception. Ba service Spring Boot cùng dùng.
- `api-gateway` chạy WebFlux và **không** phụ thuộc `core-model`.
- Frontend (SPA) ở repo khác, chỉ gọi qua `http://localhost:8080/api/...`.
- Chưa làm: AI Tutor (Python + Pinecone), Cloud Storage/CDN, auth (JWT/JWKS) trong Core LMS. Vì chưa có JWT nên gateway trả 401 cho mọi `/api/**` trừ `/api/auth/**`.

## 2. Cấu trúc thư mục

```
backend/
├── CLAUDE.md
├── pom.xml                      project cha
├── docker-compose.yml           postgres, redis, rabbitmq (project name: oep)
├── core-model/                  THƯ VIỆN dùng chung
│   └── src/main/java/com/oep/coremodel/
│       ├── constant/            MessagingConstants
│       ├── entity/              BaseEntity + 10 package theo miền:
│       │                        identity, course, enrollment, quiz, assignment,
│       │                        notification, system, social, registration, ai
│       ├── enums/               20 enum
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
| PostgreSQL (db, user, mật khẩu đều `oep`) | 5432 |
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
- Bảng DB do Hibernate tự tạo khi Core LMS khởi động (`ddl-auto: update`, chỉ cho dev). Assessment và Worker không tạo bảng nên luôn chạy Core LMS trước. `update` không đổi tên hay xóa cột cũ. Cần làm sạch DB: `docker compose down -v && docker compose up -d`.
- Mọi lệnh `docker compose` phải chạy trong thư mục có `docker-compose.yml`. Không dùng `--remove-orphans` nếu máy có container khác.

Kiểm tra: `curl http://localhost:8081/actuator/health` và `docker compose exec postgres psql -U oep -d oep -c '\dt'` (phải có 41 bảng).

## 4. Nhánh Git

Hai nhánh chính, đều được bảo vệ (không push trực tiếp, chỉ merge bằng Pull Request):

| Nhánh | Vai trò |
|---|---|
| `dev` | Nhánh tích hợp, mọi tính năng mới gộp vào đây. Là nhánh mặc định để phát triển |
| `prod` | Nhánh ổn định, chỉ chứa code đã kiểm tra, dùng để triển khai |

Quy trình:
1. Tạo nhánh từ `dev`: `feature/<tên>` hoặc `fix/<tên>`, ví dụ `feature/auth-login`.
2. Làm xong, mở Pull Request vào `dev`, có ít nhất một người review.
3. Khi `dev` ổn định và đã chạy thử đầy đủ, mở Pull Request từ `dev` vào `prod`.
4. Lỗi gấp trên `prod`: tạo `hotfix/<tên>` từ `prod`, merge vào `prod`, rồi merge ngược lại vào `dev`.

Khác biệt giữa hai nhánh: `dev` dùng `ddl-auto: update` và thông tin kết nối mặc định ở trên. Với `prod`, nhóm cần chuyển sang Flyway + `ddl-auto: validate` và đặt mật khẩu, địa chỉ DB/RabbitMQ qua biến môi trường (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `RABBIT_HOST`, `RABBIT_PORT`, `RABBIT_USER`, `RABBIT_PASSWORD`, `REDIS_HOST`, `JWK_SET_URI`) trước khi triển khai thật.