# LOAD_TEST_REPORT.md — Kiểm thử tải và chịu lỗi

Ngày chạy: 17/11/2026. Công cụ và cách chạy lại: `loadtest/README.md`. Số liệu thô: `loadtest/results/` (k6 JSON/TXT, báo cáo chaos, `docker stats`).

## 1. Môi trường

| | |
|---|---|
| Máy | 1 laptop Linux, 16 luồng CPU, 15 GB RAM — **k6, Docker và toàn bộ hệ thống dùng chung một máy** |
| Hệ thống | stack `docker-compose.prod.yml`, profile `prod,demo`: nginx → api-gateway → core-lms / assessment / worker, Postgres 16, Redis 7, RabbitMQ 3.13, MinIO |
| Giới hạn container | mỗi service Java 768 MB RAM (JVM `MaxRAMPercentage=75`), Postgres 1 GB, Redis 256 MB; không giới hạn CPU |
| Dữ liệu | dữ liệu demo + 300 học viên giả lập `loadNNNN@oep.load` ghi danh 2 khóa có quiz (`loadtest/seed-users.sql`) |
| Tải | k6 chạy trong mạng Docker, gọi thẳng gateway; mỗi người dùng ảo gửi `X-Forwarded-For` riêng (giống nhiều máy khách khác địa chỉ) |

Giới hạn của phép đo: một máy duy nhất, không có độ trễ mạng thật, DB nhỏ (vài trăm nghìn dòng chưa có). Số liệu dùng để so sánh trước/sau và tìm điểm nghẽn, **không** phải cam kết hiệu năng trên AWS.

## 2. Kết quả tải

### 2.1. Duyệt khóa học — 200 học viên đồng thời, 3 phút (`read-mix`)

Mỗi người ảo: đăng nhập, rồi lặp lại (danh sách ghi danh 30 %, danh mục khóa 25 %, outline bài học 20 %, thông báo 15 %, trang chủ công khai 10 %), nghỉ 1–3 giây.

| Chỉ số | Kết quả | Ngưỡng |
|---|---|---|
| Số request | 15 402 (≈ 85 req/s) | — |
| Lỗi | **0,00 %** | < 1 % ✅ |
| Độ trễ p95 / p99 | **23 ms / 97 ms** (trung bình 13 ms) | < 500 ms / < 1,5 s ✅ |
| `GET /api/courses` p95 | 21 ms | < 600 ms ✅ |

### 2.2. "Giờ thi" — 300 học viên cùng vào làm quiz (`quiz-spike`)

Mỗi người: đăng nhập → bắt đầu bài (kèm `Idempotency-Key`) → làm 5–15 s → nộp bài.

| Chỉ số | Kết quả | Ngưỡng |
|---|---|---|
| Hoàn thành | **300/300** bài nộp và chấm điểm | ≥ 99 % ✅ |
| Bắt đầu bài (`POST attempts`) p95 | 839 ms (trung vị 134 ms) | < 1,5 s ✅ |
| Nộp bài (`POST submit`) p95 | **44 ms** | < 2 s ✅ |
| Request lỗi | 3/906 (0,33 %) — chưa truy ra nguyên nhân, nằm trong ngưỡng | < 1 % ✅ |
| Toàn bộ request p95 | 3,6 s — **do đăng nhập** (xem dưới) | — |

Kiểm tra toàn vẹn: sau lần chạy 200 học viên, DB có đúng 200 lượt làm bài và 200 kết quả của người dùng tải.

**Điểm nghẽn quan sát được:** đăng nhập. 300 đăng nhập cùng lúc làm p95 toàn cục lên ~3 s vì băm mật khẩu bcrypt tốn CPU trên một bản `core-lms-service`. Ở đỉnh tải `assessment-service` dùng ~75 % một lõi, `core-lms-service` giữ 623/768 MB RAM (gần giới hạn), RabbitMQ có đợt CPU cao khi hàng trăm thông báo được ghi nhận cùng lúc. Hướng xử lý: chạy 2+ bản core-lms (ECS autoscaling), nới RAM lên 1 GB, giảm cost bcrypt không phải lựa chọn tốt về bảo mật.

### 2.3. Cả lớp đăng nhập sau một địa chỉ IP (`login-nat`)

100 học viên cùng IP (mạng trường/NAT) đăng nhập trong cùng một giây, mỗi người tự thử lại theo `Retry-After`.

| | Trước khi sửa | Sau khi sửa |
|---|---|---|
| Vào được trong ~6 giây | **13/100** | **85/100** (15 còn lại vào ở lần thử kế tiếp) |

## 3. Kiểm thử chịu lỗi (`chaos.py`)

Bộ thăm dò gửi ~12 req/s qua nginx → gateway (4 endpoint: trang chủ công khai, ghi danh, quiz, và một `POST` có `Idempotency-Key`). Mỗi lần dừng **một** thành phần 20 giây rồi bật lại, quan sát 45 giây.

| Sự cố | Trong lúc lỗi | Sau khi bật lại | Ghi chú |
|---|---|---|---|
| **Redis dừng** | **100 % thành công**, chậm hơn (1,2–2,4 s) | phục hồi ngay (≤ 1 s) | cache, thu hồi token, rate limit, idempotency, khóa đăng nhập đều *fail-open* |
| **RabbitMQ dừng** | **100 % thành công**, độ trễ không đổi | ngay | đăng thông báo trong lúc broker chết vẫn trả 200; 304 sự kiện nằm ở outbox (`PENDING`) và về **0** sau khi broker bật lại |
| **core-lms dừng** | ghi danh/trang chủ trả **503 sạch** + `Retry-After`; **quiz vẫn 100 %** (JWT kiểm bằng khóa đã cache) | ~21 s (thời gian khởi động lại service) | circuit breaker mở nhanh, phản hồi 5–10 ms |
| **assessment dừng** | quiz trả 503 sạch; các chức năng khác 100 % | ~20 s | lỗi không lan sang service khác |
| **Postgres dừng** | trang chủ công khai vẫn 100 % (cache Redis 60 s); các API cần DB lỗi sau ≤ 7 s | **~1 s** sau khi DB lên | timeout kết nối DB 3 s |
| **gateway bị tạo lại** (IP mới) | 502/504 trong lúc gateway chết | ~9 s | nginx tự phân giải lại tên gateway |

Kết luận: không có sự cố đơn lẻ nào làm sập toàn hệ thống; thành phần phụ (Redis, RabbitMQ) hỏng thì người dùng gần như không nhận ra; thành phần chính hỏng thì lỗi được cô lập theo service và trả mã 503 có `Retry-After`.

## 4. Lỗi tìm thấy nhờ kiểm thử và đã sửa

| # | Phát hiện | Nguyên nhân | Sửa |
|---|---|---|---|
| 1 | `read-mix` lần đầu: **27,9 % request bị 429** dù p95 chỉ 22 ms | Giới hạn tốc độ mặc định của người dùng chưa đăng nhập tính theo IP của **nginx** (mọi người dùng chung một xô 20 req/s) | khóa theo IP thật của máy khách (`X-Forwarded-For` có tin cậy `TRUSTED_PROXY_HOPS`), không theo proxy |
| 2 | Học viên cùng NAT chỉ 13/100 đăng nhập được | Spring Cloud Gateway lưu **một xô token cho mỗi khóa, không phân biệt route** nên giới hạn mặc định và giới hạn riêng của route cùng trừ một xô (mỗi request tốn 2 token, cấu hình bị route sau ghi đè) | mỗi bộ giới hạn có không gian khóa riêng (`route\|ip\|…`), mỗi route đúng **một** `RequestRateLimiter`; mặc định đăng nhập 5 req/s, burst 60 mỗi IP (`AUTH_RATE_REPLENISH/BURST`) |
| 3 | Redis chết → mọi request treo ~6 s | timeout Lettuce mặc định 60 s | `spring.data.redis.timeout=500ms`; bộ lọc idempotency chuyển sang bỏ qua Redis khi lỗi |
| 4 | Service chết → gateway trả **500** | forward fallback lỗi `ReadOnlyHttpHeaders` | thay bằng `GatewayErrorHandler`: 503/504 JSON + `Retry-After` (có log nguyên nhân) |
| 5 | Postgres chết → request treo 8–30 s | timeout kết nối mặc định | Hikari `connection-timeout=3s`, JDBC `connectTimeout=3` |
| 6 | Gateway tạo lại → **toàn bộ API treo** | nginx phân giải tên `api-gateway` một lần lúc khởi động | `resolver` + biến trong `proxy_pass` (cũng cần cho ECS/Cloud Map, nơi IP task đổi liên tục); JVM `networkaddress.ttl=10s` |
| 7 | (môi trường) host không truy cập được container | VPN `tun0` định tuyến `172.27.0.0/19`, trùng mạng Docker tự chọn | mạng compose cố định `10.231.0.0/24` (`OEP_SUBNET`) |

## 5. Mở rộng `core-lms-service` và sửa 3 request lỗi (đợt 2)

Làm sau khi có báo cáo trên, cùng kịch bản 300 học viên vào thi (`quiz-spike`), chạy khi JVM đã nóng.

**Nguyên nhân 3 request lỗi (0,33 %)**: `login` chạy trong một transaction, nên giữ kết nối DB trong suốt bước băm bcrypt (~100 ms). 300 lượt đăng nhập đồng thời làm cạn pool; lỗi sinh ra bị trang `/error` (đang yêu cầu đăng nhập) biến thành **401 rỗng** thay vì 500. Khi tôi hạ timeout lấy kết nối xuống 3 s, số lỗi tăng lên 14/917 (1,5 %) — lỗi này lộ rõ hơn chứ không phải mới.
Sửa: bước băm mật khẩu chạy **không giữ kết nối** (tra cứu và cấp token là hai transaction ngắn), pool primary 30 / `minimum-idle` 5, `/error` được phép truy cập để lỗi thật hiện đúng mã 500.

**Chạy nhiều bản**: gateway dùng bộ phân giải DNS round-robin (`RoundRobinDnsConfig`) nên một tên dịch vụ có nhiều bản ghi A (docker compose `--scale`, ECS Cloud Map) được chia đều và tự cập nhật sau vài giây. Kiểm tra: sau 300 lần đăng nhập, bản 1 xử lý 457 và bản 2 xử lý 446 lượt.

| `quiz-spike` 300 học viên | 1 bản (1 GB) | 2 bản (1 GB) |
|---|---|---|
| Request lỗi | **0** (trước đó 3–14) | **0** (3 lần chạy liên tiếp) |
| Đăng nhập p95 | 4,7 s | 3,8–4,5 s |
| Bắt đầu bài p95 | 444 ms | 106–396 ms |
| Nộp bài p95 | 133 ms | 24–26 ms |

Cải thiện đăng nhập nhỏ hơn kỳ vọng vì cả hai bản, k6, Postgres và toàn bộ stack **dùng chung 16 luồng CPU của một laptop**; tăng số bản chỉ có tác dụng đầy đủ khi mỗi bản có CPU riêng (ECS). Trong Terraform `ecs-managed`, `core-lms-service` nay là 1 vCPU / 2 GB và tự mở rộng theo CPU (tối đa 4 task); RDS mặc định `db.t4g.small` vì mỗi task giữ tối đa 30 + 20 kết nối (`max_connections` của Postgres local nâng lên 300 cho 2 bản).

## 6. Còn lại / hạn chế

- Đăng nhập là chi phí lớn nhất khi hàng trăm người vào cùng lúc: cần ≥ 2 bản `core-lms-service` ở production (đã có autoscaling cho gateway/assessment trong Terraform; cân nhắc thêm cho core-lms).
- Chưa đo trên AWS thật, chưa đo với dữ liệu lớn (hàng triệu dòng), chưa chạy tải liên tục nhiều giờ (soak test), chưa kiểm thử dừng DB replica thật (stack local không có replica).
- Sự cố mạng (mất gói, độ trễ cao) chưa mô phỏng; chaos hiện chỉ dừng/khởi động container.
