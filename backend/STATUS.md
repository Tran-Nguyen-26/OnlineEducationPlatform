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
| 6 | Notification thật (bài tập được chấm, tin nhắn Q&A mới) qua RabbitMQ → `worker-service` ghi bảng `notifications`; chuông thông báo real-time (poll 30s) cho cả 3 vai trò | ✅ | ✅ (`NotificationBell`) |
| 7 | Form sửa thông tin khóa học trên Admin UI (môn/tiêu đề/mô tả/sĩ số/chế độ học/thời gian) | ✅ (đã có từ trước) | ✅ (tab Tổng quan) |
| 8 | Dashboard thống kê Admin: `GET /api/admin/stats` (số môn/khóa học theo trạng thái, số giáo viên/học sinh, ghi danh theo trạng thái, tỷ lệ hoàn thành) | ✅ | ✅ (`DashboardPage`) |
| 9 | Trang "Tiến độ của tôi" cho học sinh: `GET /api/enrollments/progress` (số bài học hoàn thành/tổng số theo từng khóa đã ghi danh, % trung bình) | ✅ | ✅ (`/student/progress`) |
| 10 | Notification nhắc hạn: `@Scheduled` hourly job nhắc Admin khi mã mời sắp hết hạn (`InviteCodeExpiryReminderScheduler`, core-lms-service) và nhắc học sinh ACTIVE chưa làm bài khi quiz sắp đến deadline (`QuizDeadlineReminderScheduler`, assessment-service), trong vòng 24h tới — tái dùng đúng pipeline notification ở Phase 6 | ✅ | — (hiện trong `NotificationBell` có sẵn) |
| 11 | Vòng đời ghi danh (`GAPS.md` G8): học sinh tự rút khóa, Admin xem roster + loại học sinh/đánh dấu hoàn thành, tái kích hoạt khi ghi danh lại sau khi rút/bị loại; sửa kèm bug catalogue hiện sai nút "Vào học" cho enrollment không còn ACTIVE | ✅ | ✅ (`/student/progress` có nút rút khóa; tab "Học sinh" mới trong Admin course detail) |
| 12 | Roster học sinh cho giáo viên (`GAPS.md` G9): `GET /api/instructors/courses/{courseId}/students` — danh sách học sinh đã ghi danh (mọi trạng thái) kèm tiến độ hoàn thành bài học, chỉ giáo viên được gán mới xem được | ✅ | ✅ (tab "Học sinh" mới trong `CourseViewPage` của giáo viên) |
| 13 | Quên mật khẩu (`GAPS.md` G10): tự phục vụ qua `POST /api/auth/forgot-password` + `POST /api/auth/reset-password` (token một lần, hết hạn 30 phút, không cho dò email tồn tại), và Admin có thể tự tạo link đặt lại cho một user để chuyển tay (kiểm soát hơn việc tự sửa DB) | ✅ | ✅ (`/forgot-password`, `/reset-password`, nút "Tạo link đặt lại MK" trong Admin `UsersPage`) |
| 14 | Audit log hành động Admin (`GAPS.md` G5): mọi hành động ghi (tạo/sửa/xoá/đổi trạng thái) trên 13 controller Admin đều ghi 1 dòng vào bảng `audit_logs` (ai, lúc nào, hành động gì, trên đối tượng nào, metadata JSON) qua `AuditLogService` dùng chung | ✅ | ✅ (`GET /api/admin/audit-logs`, trang "Nhật ký" mới trong Admin) |
| 15 | Điều kiện hoàn thành bài học (`GAPS.md` G2): Admin đặt `lesson_completion_rules` theo bài học (thời gian xem tối thiểu, bắt buộc đã làm quiz/nộp bài tập gắn với bài học); học sinh bị chặn đánh dấu "hoàn thành" nếu chưa đạt — `timeSpentSeconds` giờ được tính thật (từ lúc mở bài học đến lúc đánh dấu), % video tối thiểu **chưa enforce được** vì trình xem nội dung chưa có video player theo dõi tiến độ xem (đã ghi rõ trong `GAPS.md`) | ✅ (trừ % video) | ✅ (modal "Điều kiện hoàn thành" trong Admin course-detail; lỗi hiển thị rõ cho học sinh khi bị chặn) |

Hạ tầng dev đang chạy thật và đã verify bằng curl: `api-gateway :8080`, `core-lms-service :8081`, `assessment-service :8082`, PostgreSQL primary `:5432` + read replica `:5433` (streaming replication, routing đọc/ghi tự động trong `core-lms-service`), Redis, RabbitMQ, MinIO (object storage, presigned PUT/GET), frontend Vite dev server `:5173`.

Tài khoản test đang có sẵn trên DB dev: `admin@oep.local` / `Admin@12345` (ADMIN), `teacher1@oep.local` / `MyNewPass123` (INSTRUCTOR, đã gán khóa "Hoa hoc 10", có sẵn 1 bài nộp đã chấm + 1 thread hỏi đáp).

## 2. Nợ kỹ thuật đã biết (tự phát hiện, chưa được yêu cầu sửa)

| # | Mục | Vị trí | Mức ảnh hưởng |
|---|---|---|---|
| ~~D1~~ | ~~`NotificationListener` chỉ log, chưa ghi bảng `notifications`~~ | — | **Đã xử lý**: publish `NotificationRequestedEvent` từ `assessment-service` (chấm bài) và `core-lms-service` (tin nhắn Q&A) → `worker-service` ghi bảng `notifications` (idempotent theo `dedupe_key`); `GET/PATCH /api/notifications` + `NotificationBell` ở frontend. Email/push thật vẫn chưa làm (chỉ in-app). |
| D2 | `ScoreSyncListener` chỉ log, chưa đồng bộ điểm từ Redis xuống `quiz_attempts` | `worker-service/.../listener/ScoreSyncListener.java` | Thấp hiện tại — vì quiz đang chấm trực tiếp trong `assessment-service`/Postgres, chưa dùng Redis cho trạng thái thi tải cao như kiến trúc gốc dự tính |
| ~~D3~~ | ~~Admin frontend chưa có form sửa thông tin cơ bản khóa học~~ | — | **Đã xử lý**: modal "Sửa thông tin" trong `OverviewTab.tsx`, dùng `PATCH /api/admin/courses/{id}` đã có sẵn. |
| D4 | RSA keypair ký JWT sinh lại mỗi lần restart `core-lms-service` | `core-lms-service/.../security/JwtKeyService.java` | Thấp (dev-only) — access token cũ hết hiệu lực ngay sau restart, user chỉ cần gọi lại `/refresh` hoặc đăng nhập lại |
| D5 | `ddl-auto: update`, không có Flyway, chưa tách config theo biến môi trường prod | toàn bộ `core-lms-service` | Phải xử lý trước khi lên `prod` (đã ghi sẵn trong `CLAUDE.md` mục 4) |
| ~~D6~~ | ~~Notification chưa có "mã mời sắp hết hạn" / "quiz sắp đến deadline"~~ | — | **Đã xử lý**: 2 scheduler mới chạy mỗi giờ (xem Phase 10), đã verify thật bằng cách tạm đổi cron sang mỗi 10s, tạo invite code/quiz có hạn trong 24h tới, xác nhận notification đúng + không bị gửi trùng (`dedupe_key`) qua nhiều lần tick, rồi trả cron về `0 0 * * * *` và khởi động lại sạch. |

## 3. Ngoài phạm vi hiện tại (chưa bắt đầu)

- AI Tutor (Python + Pinecone, hỏi đáp RAG trích nguồn).
- CDN / nén video đa độ phân giải (HLS 720p/1080p).
- Nhiều read replica, backup WAL sang Cloud Archive, triển khai thật lên GCP.
- Rate limit bằng Redis ở `api-gateway` (đã có trong sơ đồ kiến trúc, chưa code).

## 4. Đề xuất kế hoạch tiếp theo

Tất cả mục đề xuất trong các lần trước (notification, form sửa khóa học, dashboard, tiến độ học sinh, notification nhắc hạn, vòng ghi danh) **đã hoàn thành** — xem Phase 6–11 ở mục 1. Phân tích khoảng trống nghiệp vụ còn lại (không tính Flyway/prod/AI Tutor): xem `GAPS.md`, ưu tiên tiếp theo là **G9 — roster học sinh cho giáo viên**. Sau đó:

- G10 (quên mật khẩu), G5 (audit log), G2 (tự động hoàn thành theo `lesson_completion_rules`) — mức trung bình.
- G1/G3/G4/G6/G7/G11 — mức thấp, cải thiện UX/vận hành.
- Flyway + cấu hình prod (D5), AI Tutor — nằm ngoài phạm vi theo yêu cầu hiện tại của user.

Nói "làm mục N" hoặc "làm phần X" để bắt đầu — giữ đúng quy ước: build + verify bằng curl/UI thật trước khi báo xong, commit chỉ khi được yêu cầu.
