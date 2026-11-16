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
| 16 | Học phí (`BUSINESS.md` A12): `Course.feeAmount` tùy chọn, sổ cái `tuition_payments` (nhiều dòng, không sửa/xoá) ghi nhận từng khoản đã đóng theo enrollment; trạng thái `UNPAID`/`PARTIAL`/`PAID` tính tự động | ✅ | ✅ (modal "Học phí" trong tab Học sinh của Admin; học sinh thấy học phí/đã đóng trong "Tiến độ của tôi") |
| 17 | Chứng chỉ hoàn thành (`BUSINESS.md` A13): tự sinh khi enrollment chuyển `COMPLETED` (mã duy nhất `CERT-XXXXXXXX`); học sinh xem/in chứng chỉ của mình; `GET /api/certificates/verify/{code}` công khai, không cần đăng nhập, để bên thứ 3 xác thực | ✅ | ✅ (trang chứng chỉ in được tại `/student/certificates/:id`, trang xác thực công khai `/verify-certificate`) |
| 18 | Điều kiện tiên quyết giữa khóa học (`BUSINESS.md` A14): một khóa có thể yêu cầu học sinh đã `COMPLETED` (các) khóa khác trước; chặn vòng lặp điều kiện bằng BFS khi Admin thêm | ✅ | ✅ (mục "Điều kiện tiên quyết" trong tab Tổng quan của Admin course detail) |
| 19 | Danh sách chờ (`BUSINESS.md` S10): thêm `EnrollmentStatus.WAITLISTED`; ghi danh khi khóa đầy sẽ vào hàng chờ thay vì bị chặn hẳn; tự thăng `ACTIVE` theo thứ tự chờ khi có chỗ trống (học sinh rút/Admin loại một `ACTIVE`); Admin có thể thăng/loại thủ công một hàng đang chờ | ✅ | ✅ (thông báo "đang chờ" khi nhập mã; nút "Rời danh sách chờ" trong tiến độ của tôi; nút "Thăng vào học" trong roster Admin) |
| 20 | Hỏi đáp theo từng bài học (`BUSINESS.md` S9): `messages.lesson_id` tùy chọn — null là kênh chung cả khóa (như trước), có giá trị là luồng riêng của một bài học, không trộn lẫn | ✅ | ✅ (khung hỏi đáp thu nhỏ ngay dưới mỗi bài học, cả phía học sinh và giáo viên) |
| 21 | Ghi danh hàng loạt + xuất CSV (`BUSINESS.md` A15–A16): Admin dán danh sách email + chọn 1 mã mời, tự tạo tài khoản nếu chưa có (mật khẩu tạm như A4), từng dòng xử lý trong transaction riêng (`REQUIRES_NEW`) nên 1 dòng lỗi không làm hỏng cả lô; xuất roster + học phí ra CSV (UTF-8 BOM) | ✅ | ✅ (nút "Ghi danh hàng loạt" + "Xuất CSV" trong tab Học sinh của Admin) |

**2 lỗi hạ tầng tự phát hiện và sửa khi verify các mục 16–21 (không liên quan trực tiếp tới code mới, đã có từ trước):**
- `api-gateway`: route `core-lms` thiếu `/api/certificates/**` trong danh sách `Path` predicate → mọi request tới endpoint xác thực chứng chỉ bị gateway trả 404 dù `core-lms-service` xử lý đúng. Đã thêm vào `api-gateway/src/main/resources/application.yml`.
- DB: cột `enrollments.status` có sẵn một `CHECK` constraint cứng (`enrollments_status_check`, không phải do Hibernate sinh ra, không có trong bất kỳ file nào của repo) giới hạn đúng 4 giá trị cũ — chặn hẳn việc ghi `WAITLISTED` ở tầng DB dù code/entity đã đúng. Đã `ALTER TABLE ... DROP CONSTRAINT` trực tiếp trên DB dev. **Lưu ý cho môi trường khác**: nếu dựng DB mới từ đầu bằng `ddl-auto: update`, Hibernate sẽ không tự tạo lại constraint này (nó không phải do Hibernate sinh), nên chỉ cần lo cho các DB cũ đã có constraint này từ trước.

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

Toàn bộ G1–G11 trong `GAPS.md` đợt phân tích đầu, cộng thêm "Nhóm A" (học phí, chứng chỉ, điều kiện tiên quyết, danh sách chờ, hỏi đáp theo bài học, ghi danh/xuất CSV hàng loạt — xem Phase 16–21 ở mục 1) **đã hoàn thành**. Còn lại:

- **Đăng nhập Google** — đã được yêu cầu nhưng **đang chờ** Admin/chủ dự án cung cấp OAuth client id/secret thật (tạo trên Google Cloud Console); chưa làm được phần nào vì cần thông tin bên ngoài.
- G1 (ngân hàng câu hỏi), G3 (tuỳ chọn thông báo), G4 (gửi email thật), G6 (lịch sử sửa nội dung), G7 (đính kèm file Q&A), G11 (phân trang) — mức thấp, cải thiện UX/vận hành, chưa làm.
- Flyway + cấu hình prod (D5), AI Tutor — nằm ngoài phạm vi theo yêu cầu hiện tại của user.

Nói "làm mục N" hoặc "làm phần X" để bắt đầu — giữ đúng quy ước: build + verify bằng curl/UI thật trước khi báo xong, commit chỉ khi được yêu cầu.
