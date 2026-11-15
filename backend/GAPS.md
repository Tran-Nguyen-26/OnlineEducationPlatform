# GAPS.md — Phân tích khoảng trống nghiệp vụ hướng chuẩn enterprise (Project 8: Online Education Platform)

Tài liệu này **không** đổi nghiệp vụ gốc trong `BUSINESS.md` — chỉ liệt kê những gì một trung tâm học thật/enterprise LMS thường cần mà hệ thống hiện tại còn thiếu, để quyết định bổ sung tiếp. Chưa code gì ở tài liệu này. Loại trừ theo yêu cầu: không gồm Flyway/cấu hình prod (`D5`), không gồm AI Tutor — hai mục đó đã có trong `STATUS.md` mục 4.

Phân tích dựa trên việc đọc trực tiếp code, không suy diễn từ tài liệu.

## 1. Schema đã có sẵn trong `core-model` nhưng chưa có logic nào dùng (quick win — không cần đổi DB)

| # | Bảng/entity | Tình trạng thật | Việc cần làm thêm |
|---|---|---|---|
| G1 | `question_banks` (`QuestionBank`) | Hoàn toàn không dùng — câu hỏi hiện tại luôn tạo riêng cho từng quiz, không tái sử dụng được | CRUD ngân hàng câu hỏi theo môn/khóa + cho phép AdminQuizQuestionController chọn câu hỏi có sẵn từ bank thay vì luôn tạo mới |
| G2 | `lesson_completion_rules` (`LessonCompletionRule`) | Hoàn toàn không dùng — học sinh tự PATCH `IN_PROGRESS`/`COMPLETED` tuỳ ý, không có điều kiện ràng buộc nào (thời gian xem tối thiểu, % video, bắt buộc làm quiz/bài tập) | Đọc rule khi học sinh PATCH progress hoặc khi tính completion khóa học; chặn "hoàn thành" nếu chưa đạt điều kiện |
| G3 | `notification_preferences` (`NotificationPreference`) | Hoàn toàn không dùng — mọi notification luôn bắn, user không tắt được loại nào | API cho user xem/sửa tuỳ chọn theo `event_type` (in-app/email on/off); `worker-service` check trước khi ghi |
| G4 | `email_notification_events` (`EmailNotificationEvent`) | Hoàn toàn không dùng — không có `JavaMailSender`/SMTP nào trong code, notification chỉ in-app | Thêm gửi email thật (ít nhất cho các event quan trọng: tài khoản giáo viên mới, bài tập được chấm) |
| G5 | `audit_logs` (`AuditLog`) | Hoàn toàn không dùng — không hành động Admin nào (tạo/sửa/xoá/đổi trạng thái) được ghi vết | Ghi audit log ở các service Admin (ai làm gì, lúc nào, trên đối tượng nào) |
| G6 | `content_change_logs` (`ContentChangeLog`) | Hoàn toàn không dùng — `lesson_contents.version_number` tồn tại nhưng không ai tăng/ghi lịch sử khi Admin sửa nội dung | Ghi 1 dòng log + tăng version mỗi lần `PATCH /api/admin/lesson-contents/{id}` |
| G7 | `message_attachments` (`MessageAttachment`) | Hoàn toàn không dùng — tin nhắn Q&A chỉ có text, không đính kèm file được | Thêm presign-upload cho message, giống cơ chế `lesson_contents`/`submission_files` đã có |

## 2. Nghiệp vụ lõi còn thiếu hẳn (không có bất kỳ endpoint nào)

| # | Vấn đề | Bằng chứng | Vì sao đây là lỗ hổng thật |
|---|---|---|---|
| G8 | **Không có đường nào đổi `enrollments.status` sau khi tạo** | `EnrollmentService.enroll()` chỉ set `ACTIVE` lúc insert; grep toàn bộ `core-lms-service` không thấy chỗ nào set `WITHDRAWN`/`REMOVED`/`COMPLETED` | Một trung tâm học thật chắc chắn có học sinh nghỉ giữa khóa, bị buộc rời khóa, hoặc học xong — hiện tại **không thể** mô phỏng 3 tình huống này dù cột đã có sẵn trong DB và dùng trong `BUSINESS.md` |
| G9 | **Giáo viên không xem được danh sách học sinh trong khóa mình dạy** | `TeacherCourseController` chỉ có `GET /api/instructors/me/courses` và `GET /api/courses/{courseId}/content` — không có endpoint roster | Nghiệp vụ gốc nói giáo viên "xem khóa học được giao" — một lớp học thật thì giáo viên phải biết đang dạy ai, ai đã/chưa làm bài, chứ không chỉ thấy nội dung tĩnh |
| G10 | **Không có "quên mật khẩu"** | `AuthController` chỉ có `change-password` (bắt buộc đăng nhập + biết mật khẩu cũ) | Học sinh/giáo viên quên mật khẩu bị kẹt hoàn toàn, phải nhờ Admin reset tay qua DB — không chấp nhận được ở enterprise |
| G11 | **Danh sách không phân trang/tìm kiếm** | Grep toàn bộ controller: không `Pageable`/page-size nào | `GET /api/admin/users`, `/api/admin/courses` trả toàn bộ bảng — với vài nghìn user/khóa học thật sẽ chậm và khó dùng trên UI |

## 3. Đã kiểm, hoạt động đúng (không phải gap)

- `AuthService.login()`/`refresh()` đã chặn đúng khi `accountStatus = DISABLED` (ném `ACCOUNT_DISABLED`).
- Lưu ý nhỏ: access token đã phát trước khi bị khoá vẫn còn hiệu lực tới khi hết hạn tự nhiên (15 phút) — không có revocation list. Chấp nhận được vì TTL ngắn, không tính là gap ưu tiên.

## 4. Đề xuất thứ tự ưu tiên (theo giá trị nghiệp vụ thật, không phải độ khó)

**Cao — chặn nghiệp vụ lõi, nên làm trước:**
1. ~~**G8 — Vòng đời ghi danh**~~ — **Đã xử lý**, xem chi tiết dưới đây.
2. **G9 — Roster học sinh cho giáo viên**. Thiếu cái này thì "giáo viên xem khóa học được giao" vẫn chưa đầy đủ như tài liệu gốc mô tả.

### G8 đã triển khai

- Học sinh tự rút khóa: `POST /api/enrollments/{id}/withdraw` (chỉ từ `ACTIVE` → `WITHDRAWN`, set `withdrawnAt`).
- Admin xem roster + đổi trạng thái: `GET /api/admin/courses/{courseId}/enrollments`, `PATCH /api/admin/enrollments/{id}/status` (`ACTIVE` → `WITHDRAWN`/`REMOVED`/`COMPLETED`; mọi trạng thái khác đều là điểm kết, không có đường quay lại `ACTIVE` qua endpoint này).
- Ghi danh lại sau khi rút/bị loại: `EnrollmentService.enroll()` nay **tái sử dụng** đúng dòng `enrollments` cũ (do `UNIQUE(course_id, student_id)`) khi trạng thái hiện tại là `WITHDRAWN`/`REMOVED` — set lại `ACTIVE`, invite code mới, xoá `withdrawnAt`/`removedAt`; vẫn chặn nếu đang `ACTIVE`/`COMPLETED`.
- **Bug ẩn tự phát hiện và sửa kèm theo**: `StudentCourseService.listCatalog/getCourse` trước đây dùng `existsByCourseIdAndStudentId` (chỉ kiểm tồn tại dòng, không kiểm trạng thái) để quyết định hiện nút "Vào học" hay "Nhập mã" — nghĩa là học sinh đã rút/bị loại vẫn thấy nút "Vào học" dù bị chặn 403 khi click vào. Đã đổi sang `existsByCourseIdAndStudentIdAndStatus(..., ACTIVE)`.
- Frontend: nút "Rút khỏi khóa học" (có xác nhận) trong `/student/progress`; tab "Học sinh" mới trong Admin course detail (`StudentsTab.tsx`) với "Đánh dấu hoàn thành" / "Loại khỏi khóa" (có xác nhận) cho từng học sinh `ACTIVE`.
- Verify thật: rút khóa → catalogue đổi `enrolled:false` → gọi outline bị 403 → rút lần 2 bị `INVALID_STATE_TRANSITION` → nhập lại mã cũ → tái kích hoạt đúng (cùng `enrollmentId`, `enrolled:true`) → Admin đánh dấu `COMPLETED` → thử chuyển tiếp bị chặn đúng → Admin loại 1 học sinh khác đang `ACTIVE` → roster phản ánh đúng → khôi phục lại dữ liệu demo đã đổi khi test.

**Trung bình — enterprise chuẩn nên có, không chặn vận hành hiện tại:**
3. **G10 — Quên mật khẩu** (qua email hoặc Admin reset có kiểm soát).
4. **G5 — Audit log hành động Admin.**
5. **G2 — Tự động hoàn thành bài học/khóa học theo `lesson_completion_rules`.**

**Thấp — cải thiện UX/vận hành, làm sau:**
6. G1 (question bank), G3 (notification preferences), G4 (email thật), G6 (content change log), G7 (message attachment), G11 (pagination).

Nói "làm G{số}" hoặc "làm mục cao ưu tiên" để bắt đầu — vẫn giữ quy ước: build + verify thật trước khi báo xong, commit chỉ khi được yêu cầu.
