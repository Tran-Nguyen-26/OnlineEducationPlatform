# BUSINESS.md — Tài liệu nghiệp vụ (Project 8: Online Education Platform)

Tài liệu này mô tả lại nghiệp vụ gốc mà dự án phải tuân theo, đối chiếu với trạng thái triển khai hiện tại. Không đổi nghiệp vụ khi đọc tài liệu này — chỉ đối chiếu. Thiết kế bảng tương ứng từng dòng nghiệp vụ: xem `DATABASE.md` mục 2. Luồng xử lý từng bước: xem `FLOWS.md`. Trạng thái triển khai + kế hoạch: xem `STATUS.md`.

## 1. Mô hình tổng quan

Một **trung tâm dạy học** (vận hành bởi **Admin**) dạy nhiều **môn học**. Mỗi môn có nhiều **khóa học**. Mỗi khóa học có một hoặc nhiều **giáo viên** được Admin phân công giảng dạy, và nhiều **học sinh** ghi danh thông qua **mã mời (invite code)** do Admin phát hành. Có đúng 3 vai trò: `ADMIN`, `INSTRUCTOR` (giáo viên), `STUDENT` (học sinh). Không có vai trò phụ (ví dụ "trợ giảng", "quản lý khu vực").

## 2. Nghiệp vụ theo vai trò

### 2.1. Admin

| # | Nghiệp vụ | Quy tắc | Trạng thái |
|---|---|---|---|
| A1 | Quản lý nhiều môn học | Một trung tâm, nhiều `subjects`; mỗi khóa học thuộc đúng 1 môn | ✅ |
| A2 | Tạo khóa học | Gắn môn, tiêu đề/mô tả, sĩ số, thời gian học, chế độ học (tự do/tuần tự); trạng thái DRAFT → PUBLISHED → ARCHIVED/DISABLED | ✅ |
| A3 | Sinh mã mời (invite code) gửi học sinh | Mỗi khóa học có thể có nhiều mã (theo lớp/đợt); mã có thể hết hạn hoặc bị khoá tay, không xoá | ✅ |
| A4 | Tạo tài khoản giáo viên trực tiếp | Admin nhập email, hệ thống tự sinh mật khẩu ngẫu nhiên, hiển thị **đúng một lần** để Admin gửi cho giáo viên; giáo viên bắt buộc đổi mật khẩu ở lần đăng nhập đầu tiên | ✅ |
| A5 | Gán nhiều giáo viên cho một khóa học | Không có cấp bậc giữa các giáo viên được gán (không ai là "chủ" khóa học cả) — Admin luôn là bên sở hữu nội dung | ✅ |
| A6 | Soạn nội dung khóa học | Module → Lesson → Lesson Content (video/tài liệu/slide/link/text); chỉ Admin được ghi nội dung | ✅ |
| A7 | Đăng video / tài liệu | Upload qua presigned URL (không đi qua backend); theo dõi trạng thái PENDING → UPLOADED/AVAILABLE/FAILED | ✅ |
| A8 | Tạo quiz (trắc nghiệm) | Câu hỏi TRUE_FALSE/SINGLE_CHOICE/MULTIPLE_CHOICE/KEYWORD_FILL, giới hạn thời gian, hạn nộp, số lần làm, cách tính điểm (cao nhất/mới nhất/trung bình), khoá/mở quiz | ✅ |
| A9 | Tạo bài tập tự luận/nộp file | Hạn nộp, cho phép nộp muộn hay không, số lần nộp tối đa, điểm tối đa | ✅ |
| A10 | Quản lý trạng thái tài khoản người dùng | Bật/tắt (`ACTIVE`/`DISABLED`) học sinh và giáo viên — không xoá cứng | ✅ |
| A11 | Sửa thông tin cơ bản khóa học sau khi tạo | `PATCH /api/admin/courses/{id}` đã có ở backend | ⚠️ Chưa có form sửa trên UI (xem `STATUS.md` mục nợ kỹ thuật) |

### 2.2. Học sinh

| # | Nghiệp vụ | Quy tắc | Trạng thái |
|---|---|---|---|
| S1 | Tự đăng ký tài khoản | Email + mật khẩu, role luôn là STUDENT, tự đăng nhập sau khi đăng ký | ✅ |
| S2 | Đăng nhập | | ✅ |
| S3 | Xem tất cả khóa học đã publish | Thấy tiêu đề + mô tả giới thiệu dù **chưa có mã**; nội dung chi tiết (module/lesson/quiz/bài tập) **luôn bị khoá** tới khi có enrollment hợp lệ | ✅ |
| S4 | Nhập mã mời để vào học | Mã phải: thuộc đúng khóa học, còn `is_active`, chưa hết hạn; mỗi học sinh chỉ ghi danh 1 lần / khóa học (`UNIQUE(course_id, student_id)`); không có đường vào khác (không xin duyệt, không được mời riêng) | ✅ |
| S5 | Học nội dung (xem video, đọc tài liệu) | Chỉ xem được khi đang có enrollment `ACTIVE`; link xem là presigned URL có hạn | ✅ |
| S6 | Làm quiz | Đề chốt tại thời điểm bắt đầu làm (snapshot, chống sửa đề giữa chừng); tự chấm điểm ngay khi nộp cho câu trắc nghiệm/điền từ khoá; có thể bị khoá do giáo viên/admin, hết hạn, hoặc hết số lần làm | ✅ |
| S7 | Nộp bài tập | Nộp văn bản và/hoặc file; giới hạn số lần nộp; nộp muộn chỉ được nếu bài tập cho phép | ✅ |
| S8 | Xem tiến độ học tập | Theo từng lesson (chưa học/đang học/hoàn thành), theo từng enrollment | ✅ |
| S9 | Hỏi đáp trong khóa học | Một kênh chat chung mỗi khóa học, chỉ học sinh đang enroll `ACTIVE` mới gửi/xem được | ✅ |

### 2.3. Giáo viên

| # | Nghiệp vụ | Quy tắc | Trạng thái |
|---|---|---|---|
| T1 | Đăng nhập bằng tài khoản Admin cấp | Không có đăng ký tự do cho giáo viên | ✅ |
| T2 | Bắt buộc đổi mật khẩu lần đăng nhập đầu | `must_change_password=true` chặn truy cập các API khác tới khi đổi xong (frontend) | ✅ |
| T3 | Xem các khóa học được giao | Chỉ các khóa đã được Admin gán (`course_instructors`) | ✅ |
| T4 | Xem nội dung khóa học — **chỉ đọc** | Không có quyền sửa module/lesson/content/quiz/assignment — ràng buộc ở tầng API, không chỉ ẩn nút trên UI | ✅ |
| T5 | Chấm điểm bài tập của học sinh | Nhập điểm + nhận xét; chấm lại thì giữ lịch sử điểm cũ (`previous_score`, `regraded_at`) | ✅ |
| T6 | Trả lời câu hỏi học sinh | Cùng kênh chat với S9 — giáo viên được gán khóa học mới gửi/xem được | ✅ |

## 3. Quy tắc nghiệp vụ chung (áp dụng toàn hệ thống)

1. **Không hard-delete dữ liệu nghiệp vụ.** Mọi "xoá" là đổi trạng thái (`DISABLED`, `ARCHIVED`, `WITHDRAWN`, `is_active=false`, ...). Ngoại lệ: những thực thể con chưa gắn dữ liệu quan trọng (ví dụ xoá một `question_option` khi quiz chưa ai làm) vẫn có thể xoá cứng, nhưng bị chặn (409) nếu đã có dữ liệu con phụ thuộc.
2. **Một đường vào khóa học duy nhất: mã mời.** Không có flow "xin duyệt", không có "mời riêng từng học sinh", không có khóa học "riêng tư" ẩn khỏi danh sách.
3. **Admin là nguồn duy nhất tạo tài khoản giáo viên.** Không có flow giáo viên tự ứng tuyển/tự đăng ký.
4. **Giáo viên không có quyền ghi** lên bất kỳ nội dung khóa học nào (module/lesson/content/quiz/assignment) — chỉ Admin. Giáo viên chỉ ghi vào: điểm chấm bài tập, tin nhắn hỏi đáp.
5. **Không có cấp bậc giữa giáo viên** được gán cùng một khóa học.
6. **Không có đánh giá/rating khóa học** (course reviews) — tài liệu nghiệp vụ không yêu cầu.
7. **Mọi khóa đã `PUBLISHED` đều hiện giới thiệu công khai** — không có khái niệm "ẩn hẳn khỏi danh sách" (đã bỏ cột `visibility`).

## 4. Ngoài phạm vi nghiệp vụ hiện tại

Các mục sau có trong tài liệu kiến trúc (`CLAUDE.md`) nhưng **không** phải yêu cầu nghiệp vụ Admin/Giáo viên/Học sinh ở trên, nên chưa được ưu tiên triển khai:
- AI Tutor (RAG + Pinecone, hỏi đáp tự động trích nguồn).
- CDN / nén video HLS đa độ phân giải.
- Gửi email/push notification thật (hiện chỉ có log).
- Nhiều read replica, backup WAL sang Cloud Archive.

Chi tiết trạng thái triển khai từng mục: xem `STATUS.md`.
