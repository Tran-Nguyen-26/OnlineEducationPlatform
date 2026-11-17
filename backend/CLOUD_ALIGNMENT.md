# CLOUD_ALIGNMENT.md — Đối chiếu project với yêu cầu môn Cloud Application Development

Nguồn: `Cloud Application Development - Project Topics.pdf` (Project 8 — Online Education Platform) và hai bài giảng
Presentation 1–2 (từ bài toán nghiệp vụ tới kiến trúc cloud; kiến trúc ứng dụng, dữ liệu, tích hợp).
Phân tích dựa trên code và cấu hình thật của repo ở thời điểm commit `f3824ad`, không suy diễn từ tài liệu thiết kế.

## 1. Đề bài nói gì (Project 8)

- **Bài toán:** nền tảng học tập phân phối khóa học, theo dõi tiến độ và đánh giá. Người dùng: Student, Instructor, Administrator.
- **8 use case bắt buộc:** quản lý khóa học · ghi danh · nội dung video/tài liệu · tiến độ học · quiz/đánh giá · nộp bài tập · dashboard giảng viên · thông báo.
- **Đặc trưng workload:** đọc nhiều (nội dung học) · file media lớn · **đỉnh đồng thời khi làm quiz/thi** · theo dõi tiến độ/sự kiện.
- **Yêu cầu kiến trúc:** object storage + **CDN** cho nội dung · transactional store · xử lý bất đồng bộ (thông báo/media) · **cache khi có lý do** · **chiến lược autoscaling**.
- **Tính năng nâng cao gợi ý:** AI tutor bám tài liệu khóa học (RAG, trích nguồn, chặn trả lời ngoài phạm vi, đánh giá).
- **4 demo bắt buộc:** kịch bản quiz/tải đồng thời · phân phối media/nội dung hiệu quả · theo dõi tiến độ · (tùy chọn) đánh giá AI tutor.
- **Kỳ vọng chung (cả 10 đề):** MVP hướng production (không phải sản phẩm enterprise đầy đủ) · **triển khai cloud thật + CI/CD** · bảo mật · observability · ý thức chi phí · **NFR đo được** · xử lý lỗi · quyết định kiến trúc có trade-off (ADR). Câu chốt: *"Build less — but design, implement, measure and defend it well."*
- **Deliverable theo 5 chặng:** P1 Business & Architecture · P2 App/Data/Integration · P3 Security & DevOps · P4 Production Engineering (load test, failure scenario, dashboard, SLI/SLO, cost) · P5 AI Engineering (nếu có).

**Nhận định:** đề không chấm độ rộng chức năng. Trọng tâm là *bằng chứng kỹ thuật*: số liệu đo, kịch bản lỗi, triển khai tự động, giải thích được lựa chọn. Project hiện tại mạnh ở chiều "chức năng", yếu ở chiều "bằng chứng".

## 2. Đối chiếu từng yêu cầu

### 2.1. 8 use case bắt buộc — **đạt, vượt phạm vi**
Đủ 8/8, nhiều chức năng thêm (học phí, chứng chỉ, phúc khảo, điểm danh, ngân hàng câu hỏi…). Rủi ro duy nhất: **làm thừa chức năng trong khi thiếu phần được chấm** (xem 2.4–2.6). Đề nghị đóng băng chức năng mới.

### 2.2. Yêu cầu kiến trúc

| Yêu cầu | Hiện trạng thật | Đánh giá |
|---|---|---|
| Object storage | MinIO (S3-compatible); upload qua presigned URL; metadata trong DB, file trong object storage — đúng pattern bài giảng | ✅ Đạt |
| **CDN cho nội dung** | Không có. Video/PDF phát qua presigned URL tới MinIO (`view-url`, TTL 600s); video tải lên là file gốc, không transcode (HLS/nhiều độ phân giải mới có trong sơ đồ `CLAUDE.md`) | ❌ Thiếu |
| Transactional store | PostgreSQL 16, 1 primary + 1 replica; **chỉ `core-lms-service` định tuyến đọc/ghi**, `assessment-service` ghi/đọc đều vào primary | ⚠️ Đạt một phần |
| Async (thông báo / media) | RabbitMQ + `worker-service`: thông báo bất đồng bộ, idempotent theo `dedupeKey`, có tùy chọn nhóm thông báo. **Media async chưa có** (không nén video, không sinh thumbnail); `ScoreSyncListener` mới là khung TODO | ⚠️ Một nửa |
| Cache khi có lý do | Redis chỉ dùng cho **rate limit ở gateway**. Không có `@Cacheable`/cache nào cho dữ liệu nóng (outline khóa học, danh mục, bảng điểm) | ❌ Thiếu |
| Autoscaling | Không có. Chưa có Dockerfile, chưa có định nghĩa deploy; mọi service chạy `mvn spring-boot:run` | ❌ Thiếu |
| Stateless + định danh | JWT RS256 + JWKS, gateway xác thực, RBAC theo role + kiểm tra "được phân công khóa" ở tầng service, nhật ký thao tác Admin | ✅ Tốt |

### 2.3. Các điểm thiết kế đúng hướng bài giảng (nên dùng để bảo vệ)
- **Metadata → DB, file → object storage; direct upload bằng presigned URL** (không đi qua app).
- **Dedupe/idempotency ở consumer thông báo** (`dedupeKey` unique) — nhắc lại ở bài "Retry không phải lúc nào cũng an toàn".
- **Tách nghiệp vụ theo capability, không theo bảng:** core-lms (học liệu, ghi danh, tài chính) và assessment (quiz/bài tập/chấm điểm — đúng "service chịu tải cao" mà đề nhấn mạnh).
- **Công thức điểm dùng chung ở `core-model`** để hai service không lệch nhau.
- **Phân quyền kiểm chứng được** ("giáo viên chỉ chấm khóa mình, Admin chỉ xem") — khớp ví dụ requirement bảo mật cụ thể của bài giảng.

### 2.4. Demo bắt buộc

| Demo | Hiện trạng | Việc còn thiếu |
|---|---|---|
| **Quiz/tải đồng thời** | Chưa từng đo. Luồng nộp bài ghi trực tiếp Postgres: mỗi lượt nộp = 1 attempt + N `quiz_answers` + 1 `quiz_results`. Thiết kế `CLAUDE.md` (Redis hấp thụ đỉnh, worker đồng bộ xuống DB) **chưa triển khai** | Kịch bản k6/Locust, số liệu p95/lỗi/RPS, cải tiến có đo trước–sau |
| Media/nội dung hiệu quả | Presigned URL, không CDN, không transcode, không cache | CDN (hoặc cache edge) + số liệu băng thông/độ trễ; ít nhất HLS 1 độ phân giải hoặc range request chuẩn |
| Theo dõi tiến độ | ✅ Có: tiến độ bài, % video, thời gian, khóa tuần tự, hoàn thành khóa, điểm tổng kết | Chỉ cần đo tải ghi tiến độ (video-progress đã được throttle phía client) |
| AI tutor (tùy chọn) | Không có (bảng `ai` có entity nhưng không có logic) | Xem 4.7 — cân nhắc có làm hay không |

### 2.5. Kỳ vọng chung

| Kỳ vọng | Hiện trạng | Mức |
|---|---|---|
| Triển khai cloud thật | Chỉ chạy local (docker compose + `spring-boot:run`) | ❌ |
| CI/CD | Không có workflow nào; không Dockerfile; không rollback strategy | ❌ |
| IaC / containerization | Không | ❌ |
| Bảo mật | Có JWT/RBAC/audit/rate limit/CORS. **Thiếu:** HTTPS/WAF, quản lý bí mật (mật khẩu `oep`, `Admin@12345`, khóa MinIO mặc định nằm trong `application.yml`), threat model, khóa tài khoản khi đăng nhập sai, chính sách mật khẩu mạnh | ⚠️ |
| Observability | Chỉ `actuator/health`. Không metrics (Micrometer/Prometheus), không log có cấu trúc/correlation id, không tracing, không dashboard, không SLI/SLO | ❌ |
| Cost awareness | Chưa có ước tính nào | ❌ |
| **NFR đo được** | **Chưa có con số nào** (RPS, p95, availability, RPO/RTO, ngân sách) | ❌ — thiếu nền cho mọi thứ khác |
| Xử lý lỗi | Có lẻ tẻ (consumer idempotent, validate). Chưa có kịch bản lỗi chủ động: RabbitMQ/worker/DB replica/MinIO chết thì sao; chưa DLQ, retry/backoff, circuit breaker | ❌ |
| ADR / trade-off | Có `CLAUDE.md`, `BUSINESS.md`, `FLOWS.md`, `DATABASE.md` nhưng không có ADR theo mẫu Decision–Context–Options–Trade-offs | ⚠️ |
| Kiểm thử tự động | 0 file test (BE và FE) | ❌ |

### 2.6. Mức sẵn sàng cho 12 câu hỏi bảo vệ kiến trúc

| # | Câu hỏi | Trả lời được ngay? |
|---|---|---|
| 1 | Giải quyết bài toán nghiệp vụ gì | ✅ |
| 2 | Giả định workload | ❌ chưa có |
| 3 | NFR đo được | ❌ chưa có |
| 4 | Vì sao kiến trúc này | ⚠️ có sơ đồ, chưa có ADR/trade-off |
| 5 | Vì sao DB/storage này | ⚠️ trả lời được miệng (quan hệ + ACID; file → object storage), chưa viết |
| 6 | Điều gì xảy ra ở 5×/10× tải | ❌ chưa đo |
| 7 | Dependency chết thì sao | ❌ |
| 8 | Deploy và rollback | ❌ |
| 9 | Bảo vệ dữ liệu và truy cập | ⚠️ có RBAC/audit, thiếu secrets/TLS/threat model |
| 10 | Biết hệ thống khỏe bằng cách nào | ❌ |
| 11 | Chi phí | ❌ |
| 12 | Chất lượng/an toàn/chi phí AI | ❌ (chưa có AI) |

→ **Hiện đạt khoảng 1,5/12.** Đây là khoảng cách lớn nhất, và hầu hết không cần viết thêm tính năng.

## 3. Rủi ro kỹ thuật cụ thể rút ra từ code

1. **Đường ghi của quiz không chịu được đỉnh thi:** mỗi lượt nộp tạo nhiều dòng trong một transaction trên primary dùng chung với mọi thứ khác. Khi 300–500 học sinh nộp trong vài chục giây → tranh chấp kết nối Hikari, khóa dòng, tăng p95 toàn hệ thống.
2. **Idempotency chưa thật sự có ở API ghi:** cột `quiz_attempts.idempotency_key` tồn tại nhưng không dùng; nộp hai lần được chặn bằng kiểm tra trạng thái (có thể race khi bấm đúp/retry mạng). Bài giảng coi đây là chủ đề trung tâm.
3. **Hai service dùng chung một CSDL và các bảng chung** → coupling; mỗi service tự viết lại repository của nhau (đã gặp trong code). Cần ADR nói rõ đây là lựa chọn có chủ ý (đơn giản cho MVP) và ranh giới sở hữu dữ liệu.
4. **Replica chỉ phục vụ `core-lms`:** assessment (phần đọc nhiều: bảng điểm, danh sách quiz) vẫn đọc primary → lãng phí replica.
5. **Không cache nội dung đọc-nhiều** (outline khóa học, danh mục, public overview) — đúng loại dữ liệu "read-heavy learning content" mà đề nêu.
6. **Media:** không CDN, không transcode, không giới hạn dung lượng upload phía server; người xem tải thẳng từ MinIO.
7. **Bí mật mặc định trong repo** và không có TLS — sẽ bị chấm ở P3.
8. **Hạ tầng dev không tái lập được** (không IaC, không Dockerfile); ổ NTFS của máy dev còn từng làm hỏng quá trình build/commit — nên chuyển môi trường làm việc sang ext4/WSL/CI.

## 4. Kế hoạch tiếp theo (ưu tiên theo giá trị chấm điểm, không theo độ khó)

**Nguyên tắc:** đóng băng chức năng mới. 80% thời gian còn lại dành cho P1–P4 (có số liệu), P5 chỉ làm nếu còn thời gian.

### Giai đoạn A — Nền tảng "bảo vệ được" (P1) · ~3–4 ngày, chủ yếu tài liệu
1. **Workload & NFR có số** (đề xuất, cần chốt với giảng viên): 5.000 học sinh đăng ký, 1.000 DAU, đỉnh thi 300 học sinh đồng thời nộp trong 60 giây; đọc p95 < 300 ms, nộp quiz p95 < 800 ms, lỗi < 0,5%; availability 99,5% (≈ 3,6 giờ/tháng); RPO ≤ 15 phút, RTO ≤ 1 giờ; ngân sách ≤ ~150 USD/tháng. Tính RPS trung bình/đỉnh theo công thức bài giảng.
2. **Architecture Canvas + 6–8 ADR:** PostgreSQL (vì sao không NoSQL), RabbitMQ (queue vs stream), một DB dùng chung hai service (trade-off), MinIO/GCS + CDN, JWT stateless, Redis dùng ở đâu và TTL, vì sao không Kubernetes, vì sao chỉ 3 service.
3. **Threat model** (STRIDE ngắn) + bảng phân quyền → kiểm chứng được.

### Giai đoạn B — Chứng minh chịu tải quiz (P4, demo bắt buộc) · ~1 tuần
1. Viết **kịch bản k6**: đăng nhập → bắt đầu → nộp quiz với N học sinh; đo **trước khi tối ưu** (baseline).
2. Tối ưu có chọn lọc và đo lại: (a) **idempotency key** cho `start/submit`; (b) **batch insert** `quiz_answers`; (c) **Redis giữ trạng thái làm bài + hàng đợi ghi** (đã thiết kế sẵn, hoàn thiện `ScoreSyncListener`); (d) tách pool kết nối/đọc replica cho assessment; (e) cache outline/danh mục bằng Redis cache-aside với TTL và invalidate khi Admin sửa.
3. Báo cáo: biểu đồ RPS–p95–lỗi trước/sau, điểm nghẽn, kết luận 5×/10×.

### Giai đoạn C — Triển khai cloud + CI/CD (P3) · ~1 tuần
1. **Dockerfile** cho 4 service + frontend (multi-stage), `docker-compose` bản "gần production".
2. **GitHub Actions:** build + test + (quét lỗ hổng phụ thuộc) → đẩy image → deploy → smoke test → **rollback** (giữ image trước, lệnh một dòng).
3. **Chọn cloud có lý do** (tài liệu thiết kế đã nghiêng GCP): ví dụ Cloud Run (autoscale theo request, trả theo dùng — phù hợp ngân sách nhỏ) + Cloud SQL (PostgreSQL, replica) + GCS + Cloud CDN + Memorystore hoặc Redis nhỏ; RabbitMQ qua dịch vụ managed/VM nhỏ. Viết ADR "vì sao không K8s".
4. **IaC (Terraform)** cho phần tối thiểu; **secrets** chuyển sang Secret Manager/biến môi trường, bỏ mật khẩu mặc định; **HTTPS** qua load balancer/Cloud Run.
5. **Autoscaling:** cấu hình min/max instance + chỉ số kích hoạt, chứng minh bằng kết quả B.

### Giai đoạn D — Observability & độ tin cậy (P4) · ~4–5 ngày
1. **Metrics:** Micrometer + Prometheus (hoặc Cloud Monitoring); dashboard Grafana: RPS, p95, lỗi, độ sâu hàng đợi RabbitMQ, kết nối DB, thời gian nộp quiz.
2. **Log có cấu trúc + correlation id** xuyên gateway → service → worker; health/readiness riêng từng dependency.
3. **SLI/SLO** (ví dụ 99,5% request quiz < 800 ms) + cảnh báo.
4. **Kịch bản lỗi có chứng minh** (mỗi cái một demo ngắn): dừng worker → hàng đợi dồn rồi tự xử lý hết; dừng RabbitMQ → API vẫn phục vụ, thông báo bù lại sau (cần outbox hoặc retry); dừng replica → đọc tự về primary; MinIO lỗi → thông báo thân thiện. Thêm **DLQ + retry/backoff** cho consumer.

### Giai đoạn E — Media (P2/P4, demo "media hiệu quả") · ~3–5 ngày
- Phương án gọn: đặt **CDN trước object storage** + bảo đảm hỗ trợ HTTP range, header cache đúng; đo băng thông/độ trễ có và không CDN.
- Nâng cao (nếu còn thời gian): worker dùng **ffmpeg** sinh HLS 720p (+ thumbnail) bất đồng bộ — trạng thái "đang xử lý" đã có sẵn trong mô hình (`uploadStatus`).

### Giai đoạn F — Kiểm thử & bảo mật còn lại · xen kẽ
- Test tự động cho luồng lõi (đăng nhập, ghi danh, nộp quiz, tính điểm, phân quyền) chạy trong CI; test tải ở B là test hiệu năng.
- Khóa tài khoản khi đăng nhập sai nhiều lần, chính sách mật khẩu, giới hạn dung lượng/loại file upload phía server, Flyway thay `ddl-auto: update` (kèm quy trình migrate khi deploy).

### Giai đoạn G — AI tutor (P5, **tùy chọn**) · chỉ khi A–D xong
- Nếu làm, làm **nhỏ nhưng đo được**: RAG trên tài liệu khóa học mà người hỏi được quyền xem (lọc theo ghi danh), **pgvector** trong chính PostgreSQL thay vì thêm Pinecone (giảm thành phần/chi phí), bắt buộc trích nguồn, từ chối câu hỏi ngoài phạm vi khóa, bộ **20–30 câu hỏi đánh giá** (groundedness, từ chối đúng, độ trễ, token/chi phí mỗi câu).
- Nếu không còn thời gian: nói rõ trong bảo vệ đây là phần tùy chọn đã cố ý bỏ, kèm thiết kế dự kiến. Đề cho phép ("Optional").

## 5. Thứ tự đề xuất và cắt giảm

1. A (số liệu + ADR + threat model) — rẻ, mở khóa mọi thứ.
2. B (tải quiz) — demo bắt buộc quan trọng nhất.
3. C (Docker + CI/CD + cloud) — "real cloud deployment" là kỳ vọng chung.
4. D (observability + kịch bản lỗi).
5. E (CDN/media), F (test, bảo mật).
6. G (AI) chỉ khi dư thời gian.

**Cắt giảm đề nghị:** dừng thêm chức năng; không làm microservice mới; không dùng Kubernetes; không thêm công nghệ nếu không viết được ADR vì sao.

## 6. Cần quyết định từ nhóm/giảng viên
- Cloud nào được phép/có credit (GCP, AWS, hay nhà cung cấp trong nước)?
- Mốc nộp từng chặng P1–P5 và còn bao nhiêu tuần?
- Có bắt buộc làm AI tutor không, hay chỉ cần các demo còn lại?
