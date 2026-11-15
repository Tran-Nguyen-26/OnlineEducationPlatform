# STATUS.md — Trạng thái triển khai & kế hoạch tiếp theo (Project 8: Online Education Platform)

Cập nhật: xem lịch sử commit trên nhánh `nguyentt/database-replication` để biết thời điểm thật. Tài liệu nghiệp vụ: `BUSINESS.md`. Luồng xử lý: `FLOWS.md`. Schema: `DATABASE.md`. Kiến trúc/hạ tầng: `CLAUDE.md`.

## 1. Đã hoàn thành

| Phase | Nội dung | Backend | Frontend |
|---|---|---|---|
| 0 | Database/entity theo đúng nghiệp vụ (38 bảng, `core-model`) | ✅ | — |
| 1 | Xác thực: JWT RS256 + JWKS, đăng ký/đăng nhập/refresh/logout/đổi mật khẩu, admin tạo giáo viên + bắt đổi mật khẩu lần đầu | ✅ | ✅ (Login/Register/ChangePassword) |
| 2 | Admin: môn học, khóa học, gán giáo viên, mã mời, quản lý trạng thái user | ✅ | ✅ (`/admin/*`) |
| 3 | Soạn nội dung khóa học: module/lesson/lesson-content (video/PDF/slide/link/text) qua MinIO presigned URL, quiz + câu hỏi, assignment | ✅ | ✅ (tab Nội dung/Quiz/Assignment trong `CourseDetailPage`) |
| 4 | Luồng học sinh: đăng ký, duyệt catalogue, nhập mã, xem nội dung đã khoá/mở theo enrollment, làm quiz (tự chấm, snapshot đề), nộp bài tập, xem tiến độ, hỏi đáp | ✅ (`assessment-service` đứng riêng cho quiz/submission) | ✅ (`/student/*`) |
| 5 | Luồng giáo viên: xem khóa được giao (read-only), chấm bài tập (có lịch sử chấm lại), hỏi đáp | ✅ | ✅ (`/teacher/*`) |

Hạ tầng dev đang chạy thật và đã verify bằng curl: `api-gateway :8080`, `core-lms-service :8081`, `assessment-service :8082`, PostgreSQL primary `:5432` + read replica `:5433` (streaming replication, routing đọc/ghi tự động trong `core-lms-service`), Redis, RabbitMQ, MinIO (object storage, presigned PUT/GET), frontend Vite dev server `:5173`.

Tài khoản test đang có sẵn trên DB dev: `admin@oep.local` / `Admin@12345` (ADMIN), `teacher1@oep.local` / `MyNewPass123` (INSTRUCTOR, đã gán khóa "Hoa hoc 10", có sẵn 1 bài nộp đã chấm + 1 thread hỏi đáp).

## 2. Nợ kỹ thuật đã biết (tự phát hiện, chưa được yêu cầu sửa)

| # | Mục | Vị trí | Mức ảnh hưởng |
|---|---|---|---|
| D1 | `NotificationListener` chỉ `log.info(...)`, chưa ghi bảng `notifications` hay gửi email/push thật | `worker-service/.../listener/NotificationListener.java` | Trung bình — notification/nhắc hạn nộp chưa hoạt động thật dù đã có bảng & event |
| D2 | `ScoreSyncListener` chỉ log, chưa đồng bộ điểm từ Redis xuống `quiz_attempts` | `worker-service/.../listener/ScoreSyncListener.java` | Thấp hiện tại — vì quiz đang chấm trực tiếp trong `assessment-service`/Postgres, chưa dùng Redis cho trạng thái thi tải cao như kiến trúc gốc dự tính |
| D3 | Admin frontend chưa có form sửa thông tin cơ bản khóa học (title/description/subject/capacity) | `frontend/src/pages/admin/CourseDetailPage.tsx` (tab Overview) | Thấp — API `PATCH /api/admin/courses/{id}` đã có, chỉ thiếu UI |
| D4 | RSA keypair ký JWT sinh lại mỗi lần restart `core-lms-service` | `core-lms-service/.../security/JwtKeyService.java` | Thấp (dev-only) — access token cũ hết hiệu lực ngay sau restart, user chỉ cần gọi lại `/refresh` hoặc đăng nhập lại |
| D5 | `ddl-auto: update`, không có Flyway, chưa tách config theo biến môi trường prod | toàn bộ `core-lms-service` | Phải xử lý trước khi lên `prod` (đã ghi sẵn trong `CLAUDE.md` mục 4) |

## 3. Ngoài phạm vi hiện tại (chưa bắt đầu)

- AI Tutor (Python + Pinecone, hỏi đáp RAG trích nguồn).
- CDN / nén video đa độ phân giải (HLS 720p/1080p).
- Nhiều read replica, backup WAL sang Cloud Archive, triển khai thật lên GCP.
- Rate limit bằng Redis ở `api-gateway` (đã có trong sơ đồ kiến trúc, chưa code).

## 4. Đề xuất kế hoạch tiếp theo

Thứ tự đề xuất theo giá trị/công sức, **chưa làm gì** — chờ chọn:

1. **Hoàn thiện notification thật (D1)** — ghi bảng `notifications` khi có event (bài tập được chấm, mã mời sắp hết hạn, quiz sắp đến deadline...), hiển thị chuông thông báo trên frontend (cả 3 vai trò). Giá trị cao vì đụng tới cả 3 vai trò, công sức trung bình (đã có event/bảng, chỉ thiếu consumer + UI).
2. **Form sửa khóa học trên Admin UI (D3)** — nhanh, API đã có sẵn.
3. **Dashboard thống kê cho Admin** — số học sinh/giáo viên/khóa học, tỷ lệ hoàn thành, hiện `DashboardPage.tsx` đang là khung trống hay số liệu tĩnh (cần kiểm tra lại nếu muốn làm mục này).
4. **Trang "Tiến độ của tôi" cho học sinh** — tổng hợp `lesson_progress` qua tất cả khóa đã enroll thành 1 màn hình riêng (hiện tiến độ chỉ hiện trong từng khóa).
5. **Flyway + cấu hình prod (D5)** — cần làm trước khi deploy thật, không gấp nếu vẫn đang ở giai đoạn phát triển tính năng.
6. **AI Tutor** — khối lượng lớn nhất, nên làm sau khi nghiệp vụ lõi (mục 1–4 ở trên) ổn định.

Nói "làm mục N" hoặc "làm phần X" để bắt đầu — giữ đúng quy ước: build + verify bằng curl/UI thật trước khi báo xong, commit chỉ khi được yêu cầu.
