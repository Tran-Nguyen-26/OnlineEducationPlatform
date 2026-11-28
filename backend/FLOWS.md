# FLOWS.md — Tài liệu luồng (Project 8: Online Education Platform)

> **[SNAPSHOT LỊCH SỬ — Phase 1–5]:** Tài liệu này mô tả các luồng API cơ bản giai đoạn đầu (chưa bao gồm HLS `360p/720p`, Blind Grading/Appeals, Center Operations, AI Tutor RAG và các luồng bổ sung của BRD v1.4). Luồng nghiệp vụ hiện hành và kế hoạch bổ sung MVP xem tại **[BRD v1.4](../docs/BRD_Project8_OEP_v1.4.md)**, **[BUSINESS.md](BUSINESS.md)** và **[STATUS.md](STATUS.md)**.

Mô tả từng luồng nghiệp vụ theo đúng thứ tự gọi API thật đã triển khai (tên endpoint lấy trực tiếp từ controller, không suy diễn). Nghiệp vụ gốc: `BUSINESS.md`. Schema: `DATABASE.md`. Mọi request của frontend đều đi qua `api-gateway :8080`; cột "Service" cho biết gateway route request đó tới đâu.

## 1. Admin tạo tài khoản giáo viên

```mermaid
sequenceDiagram
    actor Admin
    participant GW as api-gateway
    participant LMS as core-lms-service
    Admin->>GW: POST /api/admin/teachers {email}
    GW->>LMS: forward (cần ROLE_ADMIN)
    LMS->>LMS: sinh mật khẩu random 12 ký tự<br/>tạo users(role=INSTRUCTOR) + auth_identities(must_change_password=true)
    LMS-->>Admin: trả về {email, mật khẩu tạm} — CHỈ 1 LẦN
    Admin->>Admin: gửi mật khẩu cho giáo viên (ngoài hệ thống)
```

Sau đó, lần đăng nhập đầu của giáo viên:
```mermaid
sequenceDiagram
    actor GV as Giáo viên
    participant GW as api-gateway
    participant LMS as core-lms-service
    GV->>GW: POST /api/auth/login {email, mật khẩu tạm}
    GW->>LMS: forward
    LMS-->>GV: access+refresh token, mustChangePassword=true
    Note over GV: Frontend chặn mọi trang khác,<br/>chuyển hướng /change-password
    GV->>GW: POST /api/auth/change-password {currentPassword, newPassword}
    GW->>LMS: forward
    LMS->>LMS: must_change_password=false
    LMS-->>GV: OK → vào /teacher
```

## 2. Admin tạo khóa học, gán giáo viên, sinh mã mời

```mermaid
sequenceDiagram
    actor Admin
    participant GW as api-gateway
    participant LMS as core-lms-service
    Admin->>GW: POST /api/admin/subjects {name} (nếu môn chưa có)
    Admin->>GW: POST /api/admin/courses {title, subjectId, capacity, ...}
    GW->>LMS: forward
    LMS-->>Admin: course (lifecycleStatus=DRAFT)
    Admin->>GW: POST /api/admin/courses/{courseId}/instructors {instructorId}
    Admin->>GW: PATCH /api/admin/courses/{id}/status {DRAFT→PUBLISHED}
    Admin->>GW: POST /api/admin/courses/{courseId}/invite-codes {expiresAt?}
    GW->>LMS: forward
    LMS-->>Admin: {code}
    Admin->>Admin: gửi code cho học sinh (ngoài hệ thống)
```

## 3. Admin soạn nội dung khóa học (module → lesson → content/quiz/assignment)

```mermaid
sequenceDiagram
    actor Admin
    participant GW as api-gateway
    participant LMS as core-lms-service
    participant S3 as MinIO (object storage)
    Admin->>GW: POST /api/admin/courses/{courseId}/modules {title, position}
    Admin->>GW: POST /api/admin/modules/{moduleId}/lessons {title, position}
    Admin->>GW: POST /api/admin/lessons/{lessonId}/contents {contentType: VIDEO/PDF/...}
    GW->>LMS: forward
    LMS-->>Admin: content (uploadStatus=PENDING)
    Admin->>GW: POST /api/admin/lesson-contents/{contentId}/upload-url
    GW->>LMS: forward
    LMS-->>Admin: presigned PUT url (hết hạn sau N phút)
    Admin->>S3: PUT file trực tiếp (không qua backend)
    Admin->>GW: POST /api/admin/lesson-contents/{contentId}/complete-upload
    GW->>LMS: forward
    LMS->>LMS: uploadStatus → AVAILABLE
    Admin->>GW: POST /api/admin/courses/{courseId}/quizzes {...}
    Admin->>GW: POST /api/admin/quizzes/{quizId}/questions {questionId, points}
    Admin->>GW: POST /api/admin/courses/{courseId}/assignments {...}
```

## 4. Học sinh đăng ký, duyệt khóa học, nhập mã, vào học

```mermaid
sequenceDiagram
    actor HS as Học sinh
    participant GW as api-gateway
    participant LMS as core-lms-service
    HS->>GW: POST /api/auth/register {email, password, fullName}
    GW->>LMS: forward
    LMS-->>HS: access+refresh token (role=STUDENT) — tự đăng nhập luôn
    HS->>GW: GET /api/courses
    GW->>LMS: forward
    LMS-->>HS: danh sách khóa PUBLISHED (chỉ title/description, chưa thấy nội dung)
    HS->>GW: GET /api/courses/{courseId}
    HS->>GW: POST /api/enrollments {courseId, inviteCode}
    GW->>LMS: forward
    LMS->>LMS: kiểm mã: đúng khóa, active, chưa hết hạn<br/>kiểm chưa enroll trước đó
    LMS-->>HS: enrollment (status=ACTIVE)
    HS->>GW: GET /api/courses/{courseId}/outline
    GW->>LMS: forward
    LMS-->>HS: cây module/lesson/content đầy đủ (vì đã enroll)
```

## 5. Học sinh học bài (xem nội dung + cập nhật tiến độ)

```mermaid
sequenceDiagram
    actor HS as Học sinh
    participant GW as api-gateway
    participant LMS as core-lms-service
    participant S3 as MinIO
    HS->>GW: GET /api/lesson-contents/{contentId}/view-url
    GW->>LMS: forward
    LMS->>LMS: kiểm enrollment ACTIVE của course chứa content này
    LMS-->>HS: presigned GET url (hết hạn sau N phút)
    HS->>S3: GET file trực tiếp (xem video/tài liệu)
    HS->>GW: PATCH /api/lessons/{lessonId}/progress {status: IN_PROGRESS/COMPLETED, timeSpentSeconds}
    GW->>LMS: forward
    LMS->>LMS: upsert lesson_progress(enrollment_id, lesson_id)
```

## 6. Học sinh làm quiz (snapshot đề, tự chấm)

```mermaid
sequenceDiagram
    actor HS as Học sinh
    participant GW as api-gateway
    participant ASM as assessment-service
    HS->>GW: GET /api/quizzes?courseId=...
    GW->>ASM: forward
    ASM-->>HS: danh sách quiz + bestScore (theo gradingPolicy)
    HS->>GW: POST /api/quizzes/{quizId}/attempts
    GW->>ASM: forward
    ASM->>ASM: kiểm isLocked / deadline / maxAttempts<br/>snapshot câu hỏi (ẩn đáp án đúng) vào quiz_attempts.snapshot_payload
    ASM-->>HS: attempt (status=IN_PROGRESS) + đề đã chốt
    Note over HS: làm bài trong thời gian timeLimitSeconds
    HS->>GW: POST /api/attempts/{attemptId}/submit {answers}
    GW->>ASM: forward
    ASM->>ASM: so khớp với đáp án đúng (SINGLE/MULTIPLE/TRUE_FALSE)<br/>hoặc so khớp từ khoá không phân biệt hoa thường (KEYWORD_FILL)<br/>tính TIMED_OUT nếu quá timeLimitSeconds, ghi quiz_answers + quiz_results
    ASM-->>HS: điểm (nếu quiz cho showScore=true)
```

## 7. Học sinh nộp bài tập

```mermaid
sequenceDiagram
    actor HS as Học sinh
    participant GW as api-gateway
    participant ASM as assessment-service
    participant S3 as MinIO
    HS->>GW: GET /api/assignments?courseId=...
    HS->>GW: POST /api/assignments/{assignmentId}/submissions {textContent?}
    GW->>ASM: forward
    ASM->>ASM: kiểm deadline/allowLateSubmission, kiểm maxSubmissions<br/>upsert 1 submission / (assignment, enrollment)
    ASM-->>HS: submission (status=SUBMITTED)
    opt có nộp file
        HS->>GW: POST /api/submissions/{submissionId}/files/upload-url
        GW->>ASM: forward
        ASM-->>HS: presigned PUT url
        HS->>S3: PUT file
        HS->>GW: POST /api/submission-files/{fileId}/complete-upload
    end
```

## 8. Giáo viên xem khóa học được giao (read-only) + chấm bài

```mermaid
sequenceDiagram
    actor GV as Giáo viên
    participant GW as api-gateway
    participant LMS as core-lms-service
    participant ASM as assessment-service
    GV->>GW: GET /api/instructors/me/courses
    GW->>LMS: forward
    LMS-->>GV: danh sách khóa đã được Admin gán
    GV->>GW: GET /api/courses/{courseId}/content
    GW->>LMS: forward
    LMS->>LMS: kiểm course_instructors (bắt buộc đã được gán)
    LMS-->>GV: cây nội dung đầy đủ, read-only (không có field chỉnh sửa)
    GV->>GW: GET /api/assignments/by-course/{courseId}
    GW->>ASM: forward
    ASM-->>GV: danh sách bài tập + số lượng bài đã nộp
    GV->>GW: GET /api/assignments/{assignmentId}/submissions
    GV->>GW: POST /api/submissions/{submissionId}/grade {score, feedback}
    GW->>ASM: forward
    ASM->>ASM: tạo/cập nhật assignment_grade<br/>nếu chấm lại: lưu previousScore + regradedAt
    ASM-->>GV: grade
```

## 9. Hỏi đáp trong khóa học (Học sinh ↔ Giáo viên/Admin)

Một endpoint dùng chung cho cả 3 vai trò — quyền truy cập phân nhánh theo role ngay trong service (xem `CourseMessageService.requireAccess`):

```mermaid
sequenceDiagram
    actor User as Học sinh / Giáo viên / Admin
    participant GW as api-gateway
    participant LMS as core-lms-service
    User->>GW: GET /api/courses/{courseId}/messages
    GW->>LMS: forward
    LMS->>LMS: ADMIN → luôn cho qua<br/>INSTRUCTOR → kiểm course_instructors<br/>STUDENT → kiểm enrollment ACTIVE
    LMS-->>User: danh sách message (tạo course_channel nếu chưa có)
    User->>GW: POST /api/courses/{courseId}/messages {content}
    GW->>LMS: forward (cùng kiểm quyền như trên)
    LMS-->>User: message mới (senderId, createdAt)
```

## 10. Admin quản lý trạng thái tài khoản người dùng

```mermaid
sequenceDiagram
    actor Admin
    participant GW as api-gateway
    participant LMS as core-lms-service
    Admin->>GW: GET /api/admin/users?role=&status=
    GW->>LMS: forward
    LMS-->>Admin: danh sách user
    Admin->>GW: PATCH /api/admin/users/{id}/status {ACTIVE|DISABLED}
    GW->>LMS: forward
    LMS->>LMS: không cho tự khoá chính mình (CANNOT_MODIFY_SELF)
    LMS-->>Admin: user đã cập nhật
```
