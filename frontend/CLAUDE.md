# CLAUDE.md — Project 8: Online Education Platform (Frontend)

> **Tài liệu nguồn (đồng bộ 27/11/2026):** Mọi yêu cầu nghiệp vụ, phân quyền RBAC và lộ trình bổ sung MVP xem tại [BRD v1.4](../docs/BRD_Project8_OEP_v1.4.md) và [BUSINESS.md](../backend/BUSINESS.md). Trạng thái triển khai chi tiết xem tại [STATUS.md](../backend/STATUS.md).

## 1. Tech Stack thực tế (`frontend/package.json`)

* **Core:** React 19 (`react` 19.3), TypeScript 5.9, Vite 8 (`vite --configLoader runner`)
* **Routing & Data Fetching:** React Router v7 (`react-router-dom`), TanStack Query v5 (`@tanstack/react-query`), Axios (`axios` trong `src/lib/apiClient.ts`)
* **Styling & UI:** Tailwind CSS v4 (`@tailwindcss/vite`), Lucide Icons (`lucide-react`), `clsx`, `tailwind-merge`, `class-variance-authority`, theme Indigo Aurora (`src/index.css`)
* **Media:** `hls.js` (`src/pages/student/learn/HlsVideo.tsx`) phát video HLS `360p/720p` kèm anti-seek
* **Packaging:** Multi-stage `Dockerfile` + `nginx.conf.template` (proxy `/api/` tới `api-gateway`, re-resolve DNS động)

## 2. Lệnh chạy

```bash
cd frontend
npm ci
npm run dev      # Vite dev server :5173 (gọi API qua :8080/api/...)
npm run build    # tsc -b && vite build
npm run preview
```

## 3. Cấu trúc thư mục (`frontend/src/`)

```text
frontend/src/
├── App.tsx                  Khai báo route theo vai trò (Public, STUDENT, INSTRUCTOR, ADMIN)
├── auth/                    AuthContext.tsx, ProtectedRoute.tsx
├── layouts/                 AdminLayout.tsx, StudentLayout.tsx, TeacherLayout.tsx
├── components/              Thành phần dùng chung (AuthShell, NotificationBell, CourseQaTab,
│                            GradebookView, SessionsPanel, AnnouncementsPanel, AppealModal,
│                            QuizAnalyticsModal, ui/*)
├── lib/                     apiClient.ts (Axios + JWT refresh + Idempotency-Key + retry),
│                            tokenStore.ts, markdown.tsx, csv.ts, receipt.ts
├── types/api.ts             Kiểu dữ liệu DTO khớp API backend
└── pages/
    ├── landing/             Trang chủ công khai
    ├── LoginPage.tsx, RegisterPage.tsx, ForgotPasswordPage.tsx, ResetPasswordPage.tsx,
    │   ChangePasswordPage.tsx, VerifyCertificatePage.tsx
    ├── student/             Dashboard, MyCourses/Progress, Explore, CourseViewer (HlsVideo,
    │                        LessonView, QuizzesTab, QuizTakingModal, AssignmentsTab, AiTutorPanel),
    │                        Calendar, Tasks, Grades, Certificates, Tuition (sổ cái học phí)
    ├── teacher/             Dashboard, Courses, CourseView (chỉ đọc nội dung, GradingTab,
    │                        Students, Gradebook, Sessions, Announcements, Q&A), PendingGradingPage
    ├── admin/               Dashboard, Subjects, Courses & CourseDetail (Overview, Content,
    │                        AiIndexStatusPanel, Quizzes, QuestionBankModal, Assignments, Students),
    │                        Teachers, Users, Reports, Finance (sổ cái thu tay), Broadcast, Ops, AuditLogs
    └── shared/              ProfilePage (có Export JSON cá nhân), NotificationsPage
```

## 4. Quy tắc RBAC & Nghiệp vụ bắt buộc (Đồng bộ BRD v1.4)

* **Đúng 3 vai trò RBAC:** `STUDENT`, `INSTRUCTOR`, `ADMIN` (kèm 01 **Root Admin** `is_root_admin = true` bất khả xâm phạm).
  * **Chỉ `STUDENT` được tự đăng ký** tại `/register` (có checkbox Explicit Consent; không có màn hình chọn vai trò hay đăng ký giảng viên).
  * **`INSTRUCTOR` & `ADMIN`** do nội bộ cấp phát (chỉ Root Admin được tạo/khóa `ADMIN`); bắt buộc đổi mật khẩu lần đầu (`must_change_password = true`).
* **Chỉ `ADMIN` biên soạn khóa học:** Giao diện `INSTRUCTOR` tuyệt đối chỉ đọc đối với `Module / Lesson / Content / Quiz / Assignment / Rubric`.
* **Out-of-Scope cổng thanh toán trực tuyến:** `FinancePage` / `TuitionPage` chỉ là **sổ cái ghi nhận học phí thu tay tại trung tâm** (không tích hợp cổng thanh toán online, không lưu thẻ/ngân hàng).
* **Ngôn ngữ mặc định:** Tiếng Việt (`[ĐÃ TRIỂN KHAI]`); Song ngữ Tiếng Anh (i18n) thuộc `[STRETCH]`.

## 5. Lỗi cần vá & Hạng mục FE bổ sung theo BRD v1.4 (`STATUS.md` §6)

* **Vá lỗi lệch FE–BE (`[ĐÃ TRIỂN KHAI - CẦN VÁ LỖI]`):**
  1. Bỏ `disabled` ở ô `minVideoPercentage` trong `pages/admin/course-detail/LessonItem.tsx`.
  2. Sửa checklist `pages/student/learn/LessonView.tsx` kiểm tra điểm quiz `≥ 70%` thay vì `attemptsUsed > 0`.
  3. Khóa đúng các tab AI/Quiz/Assignment/Q&A trên sidebar `CourseViewerPage.tsx` khi `readOnly` (giữ mở xem tĩnh + Mini-quiz + Mistake Cache theo BRD v1.4).
  4. Che danh tính học viên đồng nhất trên `PendingGradingPage.tsx` (phúc khảo & câu tự luận quiz).
  5. Bỏ link *"Mở trong tab mới"* lộ presigned URL video ở `LessonContent.tsx`, và cho upload file tự luận không cần nộp text trước ở `AssignmentsTab.tsx`.
* **Bổ sung MVP (`[KẾ HOẠCH BỔ SUNG - MVP]`):** Màn xác thực Email (`PENDING_VERIFICATION`), UI Root Admin, LO/Bloom (tùy chọn), phụ đề `<track>` `.vtt` + Dynamic Watermark + Heartbeat 30s, Fast-track Pre-test, Mass Exam (IndexedDB + Redis Delta Sync 20s/5s + Client Jitter 0–10s + phím tắt WCAG), Rubric + Claim/Lease 30p, nút `Flag Content/Rubric Issue`, Timestamp AI *"Hỏi AI tại mốc này"* + giới hạn `≤ 1.000` ký tự + quota 20 lượt/ngày + Cached Mistake Explanation, CSAT/NPS + Open Badges 3.0 + mã QR, Bản đồ LO & FinOps AI.
