# GAPS.md — Phân tích khoảng trống nghiệp vụ hướng chuẩn enterprise (Project 8: Online Education Platform)

Tài liệu này **không** đổi nghiệp vụ gốc trong `BUSINESS.md` — chỉ liệt kê những gì một trung tâm học thật/enterprise LMS thường cần mà hệ thống hiện tại còn thiếu, để quyết định bổ sung tiếp. Chưa code gì ở tài liệu này. Loại trừ theo yêu cầu: không gồm Flyway/cấu hình prod (`D5`), không gồm AI Tutor — hai mục đó đã có trong `STATUS.md` mục 4.

Phân tích dựa trên việc đọc trực tiếp code, không suy diễn từ tài liệu.

## 1. Schema đã có sẵn trong `core-model` nhưng chưa có logic nào dùng (quick win — không cần đổi DB)

| # | Bảng/entity | Tình trạng thật | Việc cần làm thêm |
|---|---|---|---|
| ~~G1~~ **(đã xử lý — ngân hàng câu hỏi theo khóa)** | `question_banks` (`QuestionBank`) | Hoàn toàn không dùng — câu hỏi hiện tại luôn tạo riêng cho từng quiz, không tái sử dụng được | CRUD ngân hàng câu hỏi theo môn/khóa + cho phép AdminQuizQuestionController chọn câu hỏi có sẵn từ bank thay vì luôn tạo mới |
| ~~G2~~ | ~~`lesson_completion_rules` (`LessonCompletionRule`)~~ | — | **Đã xử lý** (trừ % video), xem chi tiết dưới đây. |
| ~~G3~~ **(đã xử lý — 4 nhóm thông báo)** | `notification_preferences` (`NotificationPreference`) | Hoàn toàn không dùng — mọi notification luôn bắn, user không tắt được loại nào | API cho user xem/sửa tuỳ chọn theo `event_type` (in-app/email on/off); `worker-service` check trước khi ghi |
| G4 | `email_notification_events` (`EmailNotificationEvent`) | Hoàn toàn không dùng — không có `JavaMailSender`/SMTP nào trong code, notification chỉ in-app | Thêm gửi email thật (ít nhất cho các event quan trọng: tài khoản giáo viên mới, bài tập được chấm) |
| ~~G5~~ | ~~`audit_logs` (`AuditLog`)~~ | — | **Đã xử lý**, xem chi tiết dưới đây. |
| ~~G6~~ **(đã xử lý — lịch sử phiên bản nội dung)** | `content_change_logs` (`ContentChangeLog`) | Hoàn toàn không dùng — `lesson_contents.version_number` tồn tại nhưng không ai tăng/ghi lịch sử khi Admin sửa nội dung | Ghi 1 dòng log + tăng version mỗi lần `PATCH /api/admin/lesson-contents/{id}` |
| ~~G7~~ **(đã xử lý — đính kèm file trong hỏi đáp)** | `message_attachments` (`MessageAttachment`) | Hoàn toàn không dùng — tin nhắn Q&A chỉ có text, không đính kèm file được | Thêm presign-upload cho message, giống cơ chế `lesson_contents`/`submission_files` đã có |

## 2. Nghiệp vụ lõi còn thiếu hẳn (không có bất kỳ endpoint nào)

| # | Vấn đề | Bằng chứng | Vì sao đây là lỗ hổng thật |
|---|---|---|---|
| G8 | **Không có đường nào đổi `enrollments.status` sau khi tạo** | `EnrollmentService.enroll()` chỉ set `ACTIVE` lúc insert; grep toàn bộ `core-lms-service` không thấy chỗ nào set `WITHDRAWN`/`REMOVED`/`COMPLETED` | Một trung tâm học thật chắc chắn có học sinh nghỉ giữa khóa, bị buộc rời khóa, hoặc học xong — hiện tại **không thể** mô phỏng 3 tình huống này dù cột đã có sẵn trong DB và dùng trong `BUSINESS.md` |
| ~~G9~~ | ~~Giáo viên không xem được danh sách học sinh trong khóa mình dạy~~ | — | **Đã xử lý**, xem chi tiết dưới đây. |
| ~~G10~~ | ~~Không có "quên mật khẩu"~~ | — | **Đã xử lý**, xem chi tiết dưới đây. |
| G11 | **Danh sách không phân trang/tìm kiếm** | Grep toàn bộ controller: không `Pageable`/page-size nào | `GET /api/admin/users`, `/api/admin/courses` trả toàn bộ bảng — với vài nghìn user/khóa học thật sẽ chậm và khó dùng trên UI |

## 3. Đã kiểm, hoạt động đúng (không phải gap)

- `AuthService.login()`/`refresh()` đã chặn đúng khi `accountStatus = DISABLED` (ném `ACCOUNT_DISABLED`).
- Lưu ý nhỏ: access token đã phát trước khi bị khoá vẫn còn hiệu lực tới khi hết hạn tự nhiên (15 phút) — không có revocation list. Chấp nhận được vì TTL ngắn, không tính là gap ưu tiên.

## 4. Đề xuất thứ tự ưu tiên (theo giá trị nghiệp vụ thật, không phải độ khó)

**Cao — chặn nghiệp vụ lõi:**
1. ~~**G8 — Vòng đời ghi danh**~~ — **Đã xử lý**.
2. ~~**G9 — Roster học sinh cho giáo viên**~~ — **Đã xử lý**.

### G8 đã triển khai

- Học sinh tự rút khóa: `POST /api/enrollments/{id}/withdraw` (chỉ từ `ACTIVE` → `WITHDRAWN`, set `withdrawnAt`).
- Admin xem roster + đổi trạng thái: `GET /api/admin/courses/{courseId}/enrollments`, `PATCH /api/admin/enrollments/{id}/status` (`ACTIVE` → `WITHDRAWN`/`REMOVED`/`COMPLETED`; mọi trạng thái khác đều là điểm kết, không có đường quay lại `ACTIVE` qua endpoint này).
- Ghi danh lại sau khi rút/bị loại: `EnrollmentService.enroll()` nay **tái sử dụng** đúng dòng `enrollments` cũ (do `UNIQUE(course_id, student_id)`) khi trạng thái hiện tại là `WITHDRAWN`/`REMOVED` — set lại `ACTIVE`, invite code mới, xoá `withdrawnAt`/`removedAt`; vẫn chặn nếu đang `ACTIVE`/`COMPLETED`.
- **Bug ẩn tự phát hiện và sửa kèm theo**: `StudentCourseService.listCatalog/getCourse` trước đây dùng `existsByCourseIdAndStudentId` (chỉ kiểm tồn tại dòng, không kiểm trạng thái) để quyết định hiện nút "Vào học" hay "Nhập mã" — nghĩa là học sinh đã rút/bị loại vẫn thấy nút "Vào học" dù bị chặn 403 khi click vào. Đã đổi sang `existsByCourseIdAndStudentIdAndStatus(..., ACTIVE)`.
- Frontend: nút "Rút khỏi khóa học" (có xác nhận) trong `/student/progress`; tab "Học sinh" mới trong Admin course detail (`StudentsTab.tsx`) với "Đánh dấu hoàn thành" / "Loại khỏi khóa" (có xác nhận) cho từng học sinh `ACTIVE`.
- Verify thật: rút khóa → catalogue đổi `enrolled:false` → gọi outline bị 403 → rút lần 2 bị `INVALID_STATE_TRANSITION` → nhập lại mã cũ → tái kích hoạt đúng (cùng `enrollmentId`, `enrolled:true`) → Admin đánh dấu `COMPLETED` → thử chuyển tiếp bị chặn đúng → Admin loại 1 học sinh khác đang `ACTIVE` → roster phản ánh đúng → khôi phục lại dữ liệu demo đã đổi khi test.

### G9 đã triển khai

- `GET /api/instructors/courses/{courseId}/students` (chỉ giáo viên được gán — tái dùng `TeacherAccessService.requireAssignedInstructor`, 403 nếu không): danh sách mọi học sinh từng ghi danh (mọi trạng thái, không chỉ ACTIVE) kèm `enrolledAt`, trạng thái, và tiến độ hoàn thành bài học (`completedLessons`/`totalLessons`/`progressPercent`), tính giống `StudentCourseContentService.myProgress`.
- Frontend: tab "Học sinh" mới trong `CourseViewPage` (giáo viên), bảng có thanh tiến độ.
- Verify thật: `teacher2` xem roster khóa "Toan 10 co ban" → đúng 2 học sinh (1 ACTIVE, 1 WITHDRAWN) với tiến độ 0/3; `teacher1` (không được gán khóa này) gọi cùng endpoint → đúng 403 `FORBIDDEN`.

### G10 đã triển khai

- Tự phục vụ: `POST /api/auth/forgot-password { email }` — luôn trả 204 dù email có tồn tại hay không (chặn dò email), nếu có tài khoản local thì tạo token một lần (hash SHA-256 lưu DB, hiệu lực 30 phút) và log ra (đứng vai trò "sẽ gửi email" — gửi email thật vẫn là G4, chưa làm). `POST /api/auth/reset-password { token, newPassword }` — đặt mật khẩu mới, đánh dấu token đã dùng, thu hồi toàn bộ refresh token hiện có của user (buộc đăng nhập lại mọi nơi).
- Admin-kiểm soát: `POST /api/admin/users/{id}/reset-password-link` (ADMIN) — tạo cùng loại token, trả thẳng token/link cho Admin để chuyển tay cho user (không cần sửa DB tay như trước).
- Bảng mới `password_reset_tokens` (`core-model`, Hibernate tự tạo qua `ddl-auto: update`).
- Frontend: `/forgot-password`, `/reset-password?token=...` (public), link "Quên mật khẩu?" trên `LoginPage`; nút "Tạo link đặt lại MK" + modal hiện link trong Admin `UsersPage`.
- Verify thật trên tài khoản `admin@oep.local`: forgot-password → token log ra đúng → reset-password đổi mật khẩu thành công → dùng lại token cũ bị từ chối (`INVALID_RESET_TOKEN`) → đăng nhập mật khẩu cũ bị từ chối, mật khẩu mới đăng nhập được → dùng chính endpoint Admin tạo link mới, đặt lại về đúng mật khẩu gốc để không đổi dữ liệu thật của user.

### G5 đã triển khai

- `AuditLogService.record(actorId, action, targetType, targetId, metadata)` dùng chung — ghi 1 dòng vào `audit_logs` (metadata lưu dạng JSON qua `ObjectMapper`). Gọi ngay sau khi 1 hành động ghi của Admin thành công, ở tầng controller (không ở service, để giữ service thuần nghiệp vụ).
- Đã gắn vào toàn bộ 13 controller Admin có hành động ghi: course (create/update/status), subject (create/update), course-instructor (assign/unassign), invite-code (generate/deactivate), module/lesson/lesson-content (create/update/delete), quiz (create/update/lock/unlock/delete), quiz-question (add/remove), assignment (create/update/delete), enrollment (update-status), user (create-teacher/update-status/generate-reset-link).
- `GET /api/admin/audit-logs` — danh sách mọi bản ghi, mới nhất trước (chưa phân trang, xem G11).
- Frontend: trang "Nhật ký" mới trong Admin, bảng thời gian/người thực hiện/hành động/đối tượng/chi tiết.
- Verify thật: Admin tạo 1 môn học mới → audit log có đúng dòng `CREATE_SUBJECT` với `actorEmail=admin@oep.local`, `targetId` đúng id môn học vừa tạo, `metadata` đúng tên môn; sửa lại môn đó (đổi tên, tắt active) → có thêm dòng `UPDATE_SUBJECT`.

### G2 đã triển khai

- Admin đặt điều kiện hoàn thành theo từng bài học: `PUT/GET/DELETE /api/admin/lessons/{lessonId}/completion-rule` (upsert đúng 1 dòng `lesson_completion_rules`, do cột `lesson_id` là `unique`).
- Khi học sinh PATCH bài học sang `COMPLETED`, `StudentCourseContentService.updateLessonProgress` đọc rule (nếu có) và chặn bằng lỗi `COMPLETION_RULE_NOT_MET` (409) nếu chưa đạt:
  - `minimumTimeSeconds`: so với `lesson_progress.time_spent_seconds`, nay được tính thật bằng thời gian trôi qua từ `startedAt` (lúc bài học được mở lần đầu, frontend tự PATCH `IN_PROGRESS` khi học sinh chọn bài học) tới lúc PATCH hiện tại — trước đây cột này tồn tại nhưng không ai từng ghi, luôn là 0.
  - `requireQuiz`/`requireAssignment`: kiểm tra có ít nhất 1 `quiz_attempts`/`assignment_submissions` gắn với enrollment hiện tại, cho bất kỳ quiz/assignment nào có `lesson_id` trỏ tới bài học này (`QuizRepository.findByLesson_Id`, `AssignmentRepository.findByLesson_Id`). `QuizAttemptRepository`/`AssignmentSubmissionRepository` mới thêm vào `core-lms-service` chỉ để đọc tồn tại — theo đúng pattern nhân bản repository giữa service đã có sẵn (`EnrollmentRepository`/`CourseInstructorRepository` cũng có bản riêng ở `assessment-service`).
  - `minVideoPercentage`: **chưa enforce được** — trình xem nội dung hiện chỉ mở file ở tab mới (`window.open`), không có `<video>` player trong app để theo dõi % đã xem. Cột/field vẫn lưu được qua form Admin, chỉ là chưa có gì đọc nó. Cần một tính năng video player riêng (ngoài phạm vi hợp lý của G2) để làm tiếp.
- Frontend: modal "Điều kiện hoàn thành bài học" trong Admin course-detail (nút cạnh "Nội dung" của mỗi bài học); học sinh thấy lỗi rõ ràng (tiếng Việt) ngay dưới nút "Đánh dấu hoàn thành" nếu bị chặn.
- Verify thật: đặt `minimumTimeSeconds=600` cho 1 bài học → học sinh mở bài (PATCH `IN_PROGRESS`) → PATCH `COMPLETED` ngay bị chặn đúng lỗi "chưa đủ 600 giây"; đổi `minimumTimeSeconds=0` → hoàn thành được ngay; đổi `requireQuiz=true` (học sinh chưa làm quiz nào của bài học) → PATCH `COMPLETED` bị chặn đúng "cần làm quiz trước"; xoá rule để dọn dữ liệu test.

**Trung bình — enterprise chuẩn nên có, không chặn vận hành hiện tại:**
3. ~~**G5 — Audit log hành động Admin.**~~ — **Đã xử lý**.
4. ~~**G2 — Tự động hoàn thành bài học/khóa học theo `lesson_completion_rules`.**~~ — **Đã xử lý** (trừ % video).

**Thấp — cải thiện UX/vận hành, làm sau:**
5. G1 (question bank), G3 (notification preferences), G4 (email thật — sẽ thay thế phần log token trong G10 ở trên), G6 (content change log), G7 (message attachment), G11 (pagination).

**Chặn vì cần thông tin từ bên ngoài:**
6. **Đăng nhập Google** — cần OAuth client id/secret thật (tạo trên Google Cloud Console) từ chủ dự án trước mới code được; chưa bắt đầu.

Nói "làm G{số}" hoặc "làm mục cao ưu tiên" để bắt đầu — vẫn giữ quy ước: build + verify thật trước khi báo xong, commit chỉ khi được yêu cầu.

## 5. "Nhóm A" — nâng cấp theo chuẩn nghiệp vụ thực tế (so sánh với phần mềm tương tự), đã triển khai

Phân tích dựa trên so sánh với nghiệp vụ thật của các nền tảng online self-paced LMS tương tự (Udemy/Coursera-style) và phần mềm quản lý trung tâm dạy học. Loại trừ mọi thứ trái với quy tắc đã chốt trong `BUSINESS.md` mục 3 (không thêm vai trò phụ huynh, không thêm lịch học buổi cụ thể/điểm danh, không thêm đánh giá khóa học — xem lựa chọn của user khi được hỏi).

- **Học phí**: `Course.feeAmount` (tùy chọn) + sổ cái `tuition_payments` (nhiều dòng/enrollment, Admin ghi nhận thủ công, không sửa/xoá dòng cũ). `TuitionPaymentService.status()` tính `UNPAID`/`PARTIAL`/`PAID` từ tổng đã đóng so với học phí. `GET/POST /api/admin/enrollments/{id}/payments`. Hiện ở cả roster Admin và "Tiến độ của tôi" của học sinh.
- **Chứng chỉ hoàn thành**: entity `Certificate` (1-1 với enrollment, mã `CERT-XXXXXXXX` duy nhất), `CertificateService.issueIfAbsent()` gọi tự động trong `AdminEnrollmentService.updateStatus` khi target là `COMPLETED`. `GET /api/enrollments/{id}/certificate` (chỉ chủ enrollment) + `GET /api/certificates/verify/{code}` (công khai, không cần đăng nhập — đúng kiểu "verify certificate" của Udemy/Coursera). Trang chứng chỉ ở frontend dùng CSS in được (`window.print()`), không sinh PDF ở backend để tránh thêm dependency nặng chưa cần thiết.
- **Điều kiện tiên quyết**: entity `CoursePrerequisite` (courseId, prerequisiteCourseId, unique cặp). `AdminCoursePrerequisiteService.add()` chặn vòng lặp bằng BFS trên đồ thị "cần" trước khi lưu. `EnrollmentService.enroll()` kiểm tra tất cả điều kiện tiên quyết đã `COMPLETED` chưa, nếu chưa thì chặn `PREREQUISITE_NOT_MET` kèm tên khóa còn thiếu.
- **Danh sách chờ**: thêm `EnrollmentStatus.WAITLISTED`. `enroll()` khi khóa đầy (theo `capacity`) tạo enrollment `WAITLISTED` thay vì chặn `COURSE_FULL`. `WaitlistService.promoteNext(courseId)` tự thăng người chờ lâu nhất (`enrolledAt` sớm nhất) lên `ACTIVE` khi có 1 chỗ trống — gọi sau mỗi lần một enrollment `ACTIVE` rời đi (học sinh tự rút hoặc Admin đổi trạng thái). Admin cũng có thể thăng/loại thủ công một hàng đang chờ (`AdminEnrollmentService.updateStatus` nay nhận cả nguồn `WAITLISTED`).
- **Hỏi đáp theo bài học**: `messages.lesson_id` tùy chọn (null = kênh chung cả khóa, giữ nguyên hành vi cũ). `CourseMessageService.list/post` nhận thêm `lessonId`; hai luồng không trộn lẫn. Component `CourseQaTab` dùng lại cho cả 2 ngữ cảnh qua prop `lessonId`/`compact`.
- **Ghi danh hàng loạt + xuất CSV**: `POST /api/admin/courses/{courseId}/bulk-enroll` nhận 1 `inviteCodeId` có sẵn + danh sách email; mỗi dòng xử lý trong transaction riêng (`BulkEnrollRowService`, `REQUIRES_NEW`) — một dòng lỗi (sai định dạng, trùng, không phải tài khoản học sinh) chỉ làm dòng đó lỗi, không rollback cả lô. Tài khoản học sinh mới được tạo kèm mật khẩu tạm như khi Admin tạo giáo viên. `GET /api/admin/courses/{courseId}/enrollments/export` xuất CSV UTF-8 (có BOM để Excel đọc đúng dấu tiếng Việt).

**3 lỗi thật tự phát hiện và sửa khi code/verify nhóm này:**
1. `api-gateway` thiếu route `/api/certificates/**` — gateway tự trả 404 trước khi tới được `core-lms-service` (đã sửa `application.yml`, xem `STATUS.md`).
2. DB có sẵn `CHECK` constraint cứng `enrollments_status_check` (không phải do Hibernate sinh, không có trong repo) chỉ cho 4 giá trị cũ — chặn ghi `WAITLISTED` ở tầng DB dù code đúng (đã `ALTER TABLE ... DROP CONSTRAINT` trên DB dev, xem `STATUS.md`).
3. Lỗi tự gây ra trong lúc code `AdminBulkEnrollmentService`: tạo `UserProfile` mới cho tài khoản chưa có mà quên set `fullName` (cột `NOT NULL`) → lỗi 500 khi tạo học sinh mới qua ghi danh hàng loạt; đồng thời phát hiện và sửa luôn vấn đề thiết kế transaction (1 dòng lỗi làm rollback cả lô) bằng cách tách `BulkEnrollRowService` với `REQUIRES_NEW`.

Verify thật từng mục: đặt học phí → ghi 2 khoản thanh toán → đúng trạng thái `PARTIAL` rồi `PAID`; đánh dấu hoàn thành → chứng chỉ tự sinh → xác thực công khai đúng/sai mã; đặt điều kiện tiên quyết → chặn đúng → hoàn thành khóa điều kiện → ghi danh được; test vòng lặp điều kiện bị chặn đúng; tạo khóa capacity=1 → 2 học sinh ghi danh (1 ACTIVE, 1 WAITLISTED) → rút người ACTIVE → người chờ tự lên ACTIVE đúng thứ tự → Admin thăng/loại thủ công đúng; đăng tin nhắn theo bài học và theo khóa không lẫn vào nhau, cả 2 vai trò học sinh/giáo viên; ghi danh hàng loạt 1 email cũ + 1 email mới + 1 trùng + 1 không phải học sinh → đúng cả 4 kết quả, không rollback lẫn nhau; xuất CSV đúng BOM + nội dung. Dữ liệu demo đã đổi khi test (ví dụ trạng thái ghi danh của `student3` trong Vật lý) đã khôi phục lại như trước khi test; tài khoản/enrollment tạo ra riêng cho test đã bị vô hiệu hoá/loại bỏ.
