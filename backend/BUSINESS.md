# BUSINESS.md — Tài liệu nghiệp vụ (Project 8: Online Education Platform)

Tài liệu này được **đồng bộ hoàn chỉnh 100% với [BRD v1.4](../docs/BRD_Project8_OEP_v1.4.md)** (cập nhật 27/11/2026 — bản chốt toàn bộ Mục 1–8 & Phụ lục A). Thiết kế bảng tương ứng từng dòng nghiệp vụ: xem `DATABASE.md` mục 2. Luồng xử lý từng bước: xem `FLOWS.md`. Trạng thái triển khai + kế hoạch: xem `STATUS.md`.

**Quy ước trạng thái:** `✅` = Đã triển khai khớp v1.4 · `🔧` = Đã triển khai nhưng cần vá/nâng cấp theo v1.4 · `📝` = Kế hoạch bổ sung MVP (chưa code hoặc mới có phần nền) · `STRETCH` = Tính năng mở rộng sau Core MVP.

## 1. Mô hình tổng quan

Một **trung tâm dạy học** (vận hành bởi **Admin**) dạy nhiều **môn học** (`subjects`). Mỗi môn có nhiều **khóa học** (`courses`). Mỗi khóa học có một hoặc nhiều **giáo viên** được Admin phân công giảng dạy (khóa có bài tự luận yêu cầu tối thiểu 02 giáo viên phụ trách hoặc ≥ 02 giáo viên `ACTIVE` trong cùng môn học khi phát hành), và nhiều **học viên** ghi danh thông qua **mã mời theo lớp** (`max_uses > 1`) hoặc **mã kích hoạt 1 lần** (`max_uses = 1`). Có đúng **3 vai trò RBAC**: `ADMIN`, `INSTRUCTOR`, `STUDENT` (kèm cờ hệ thống `is_root_admin = true` cho duy nhất 01 tài khoản **Root Admin** không thể bị xóa/vô hiệu hóa/ẩn danh hóa).

## 2. Nghiệp vụ theo vai trò

### 2.1. Admin

| # | Nghiệp vụ | Quy tắc (Đồng bộ BRD v1.4) | Trạng thái |
|---|---|---|---|
| A1 | Quản lý nhiều môn học | Một trung tâm, nhiều `subjects`; mỗi khóa học thuộc đúng 1 môn | ✅ |
| A2 | Tạo & phát hành khóa học | Gắn môn, tiêu đề/mô tả, sĩ số, chế độ học (**mặc định `SEQUENTIAL`**, tùy chọn `FREE_NAVIGATION`), thời hạn linh hoạt (**theo đợt `startAt/endAt`**, **theo cá nhân `access_period_days` 90/180 ngày**, hoặc **kết hợp cả 2**); trạng thái `DRAFT → PUBLISHED → ARCHIVED/DISABLED`. **Publish Validation 2 lớp:** (1) Mọi khóa cần ≥ 1 Module/Lesson/học liệu, tổng `weight_%` kiểm tra chính thức = 100%, khóa có tự luận cần ≥ 2 GV phụ trách/cùng môn; (2) Nếu khóa có khai báo LO thì mỗi LO cần ≥ 1 học liệu, ≥ 3 Mini-quiz, ≥ 1 câu thi chính thức. | 🔧 (đổi mặc định `SEQUENTIAL`, thêm `access_period_days`, Publish 2 lớp) |
| A3 | Sinh mã mời / mã kích hoạt & mã gia hạn | Hỗ trợ 2 chế độ: **mã mời theo lớp** (`max_uses > 1`) và **mã kích hoạt 1 lần** (`max_uses = 1`, gắn `1 tài khoản – 1 khóa học`); bổ sung tạo lô **CSV/QR**, thu hồi, cấp lại và **Mã gia hạn (Extension Code)** cộng ngày trực tiếp vào `enrollments.access_expires_at`. | 🔧 (đã có mã lẻ & `max_uses`; 📝 lô CSV/QR, cấp lại, Mã gia hạn) |
| A4 | Tạo tài khoản cả 3 vai trò & bảo vệ Root Admin | Admin tạo được tài khoản `INSTRUCTOR` và `STUDENT` (mật khẩu tạm ngẫu nhiên hiển thị 1 lần, bắt buộc đổi mật khẩu lần đầu, miễn verify email). **Chỉ Root Admin** (`is_root_admin = true`) mới được tạo/khóa tài khoản `ADMIN` khác; Root Admin bất khả xâm phạm. | 🔧 (đã có tạo GV/HV; 📝 tạo `ADMIN` & khóa bảo vệ Root Admin) |
| A5 | Gán nhiều giáo viên cho một khóa học | Không có cấp bậc giữa các giáo viên được gán — Admin luôn là bên sở hữu nội dung; khóa có bài tự luận cần ≥ 2 GV (hoặc ≥ 2 GV cùng môn) để đảm bảo phúc khảo độc lập. | ✅ (📝 ràng buộc ≥ 2 GV khi Publish) |
| A6 | Soạn nội dung khóa học & LO tùy chọn | `Module → Lesson → Lesson Content`; chỉ Admin được ghi nội dung. Hỗ trợ gắn **Learning Outcome (LO) & thang Bloom (Nhớ/Hiểu/Vận dụng) tùy chọn** theo từng khóa. | ✅ (📝 LO & Bloom tùy chọn) |
| A7 | Đăng video, phụ đề `.vtt` tùy chọn & tài liệu | Upload qua presigned URL (`.mp4 ≤ 2 GB`, `.vtt/.srt ≤ 5 MB` tùy chọn, `PDF/DOCX ≤ 20 MB`, xác thực kích thước thật trên S3); Async FFmpeg worker đóng gói **HLS 360p/720p**; nâng cấp bảo mật Edge HLS (đang nghiên cứu AWS CloudFront OAC + Signed Cookies vs. Hybrid Cloudflare R2 + Worker HMAC) + Dynamic Watermark. | 🔧 (đã có presigned + HLS 360p/720p; 📝 UI/parser `.vtt`, Edge Signed Cookies, Watermark) |
| A8 | Tạo & lên lịch 4 loại Quiz | Phân loại `PRE_TEST` (`max=1`), `POST_TEST` (ẩn đáp án/AI động + Remediation 24h), `MINI_QUIZ` (`Unlimited`, gợi ý Socratic `< 70%`), `MASS_EXAM` (`max=1`, lịch `T_start/T_end`, vào muộn ≤ 15p, 500 CCU, Pre-warming, Exam Panic Mode). Quản lý Ngân hàng câu hỏi thủ công + Import/Export CSV/JSON (`✅`); AI Quiz Generator (`STRETCH`). | 🔧 (đã có Quiz chung & CSV/JSON bank; 📝 4 loại Quiz & Mass Exam engine) |
| A9 | Tạo bài tập tự luận & Rubric | Rich-text/Markdown hoặc PDF/DOCX ≤ 20 MB (kiểm tra ≥ 50 từ / 300 ký tự text-searchable, kiểm dung lượng thật S3), hạn nộp, `max_submissions`, **Rubric từng tiêu chí**, Blind Grading mặc định `true`. | 🔧 (đã có Blind Grading & 20 MB; 📝 Rubric, PDF text-searchable, `headObject`) |
| A10 | Quản lý trạng thái tài khoản người dùng | Bật/tắt (`ACTIVE`/`DISABLED`) + `PENDING_VERIFICATION` + `DORMANT` (ngủ đông 24 tháng) — không xóa cứng; cấm vô hiệu hóa/ẩn danh hóa Root Admin. | 🔧 (📝 `PENDING_VERIFICATION`, `DORMANT`, khóa Root Admin) |
| A11 | Sửa thông tin cơ bản khóa học sau khi tạo | `PATCH /api/admin/courses/{id}` đã có ở backend | ✅ |
| A12 | Sổ cái học phí thủ công & biên lai *(Center Ops)* | **Out-of-Scope cổng thanh toán trực tuyến & không lưu thẻ/ngân hàng.** Mỗi khóa học có thể đặt `feeAmount` tùy chọn; Admin ghi nhận từng khoản `PAYMENT`/`DISCOUNT`/`REFUND` theo enrollment (sổ cái bất biến, không sửa/xóa), tự tính `UNPAID`/`PARTIAL`/`PAID` và in biên lai | ✅ |
| A13 | Cấp chứng chỉ số (Open Badges 3.0) & CSAT/NPS | Tự động xét hoàn thành (100% Module + điểm trọng số bài thi chính thức `weight_% = 100%` ≥ 70%, loại Pre-test) + **khảo sát CSAT/NPS** → xuất **1EdTech Open Badges 3.0** (JSON-LD + chữ ký mật mã + QR/URL verify công khai) | 🔧 (đã có tự hoàn thành ≥ 70% & verify URL; 📝 Open Badges 3.0 + QR & CSAT/NPS) |
| A14 | Điều kiện tiên quyết giữa khóa học *(Center Ops)* | Một khóa có thể yêu cầu học viên đã `COMPLETED` một hoặc nhiều khóa khác trước khi ghi danh; chặn vòng lặp bằng BFS | ✅ |
| A15 | Ghi danh hàng loạt qua danh sách email *(Center Ops)* | Admin chọn 1 mã mời của khóa + dán danh sách email; học viên chưa có tài khoản được tạo kèm mật khẩu tạm; từng dòng xử lý độc lập (`REQUIRES_NEW`) | ✅ |
| A16 | Xuất danh sách ghi danh ra CSV & Lịch học/Điểm danh *(Center Ops)* | Xuất roster/học phí CSV UTF-8 (BOM); quản lý lịch buổi học & điểm danh (tùy chọn cho khóa có buổi live/hỗ trợ) | ✅ |

### 2.2. Học viên (`STUDENT`)

| # | Nghiệp vụ | Quy tắc (Đồng bộ BRD v1.4) | Trạng thái |
|---|---|---|---|
| S1 | Tự đăng ký tài khoản & xác thực Email | Email + mật khẩu + **Explicit Consent**, role luôn là `STUDENT`, khởi tạo trạng thái `PENDING_VERIFICATION`, bắt buộc **xác thực Email OTP/Link (15–30p)** trước khi đăng nhập. | 🔧 (đã có đăng ký + consent; 📝 `PENDING_VERIFICATION` & OTP/Link email) |
| S2 | Đăng nhập & kiểm soát thiết bị / luồng phát | Lưu tối đa **03 thiết bị** (đá cũ nhất); khóa cứng **01 luồng phát video hoạt động** (Redis Heartbeat 30s); **Single-Device Exam Lock** + `Device Handover Audit Log` khi đang thi. | 🔧 (đã có JWT/Refresh/Revocation; 📝 3 thiết bị, 1 luồng video, Exam Lock) |
| S3 | Xem tất cả khóa học đã publish | Thấy tiêu đề + mô tả giới thiệu dù chưa có mã; nội dung chi tiết luôn bị khoá tới khi có enrollment hợp lệ | ✅ |
| S4 | Nhập mã kích hoạt / mã mời để vào học | Mã hợp lệ, còn hiệu lực/lượt; mỗi học viên 1 enrollment / khóa (`UNIQUE(course_id, student_id)`). Hết thời hạn học (`access_expires_at` / `ENDED`) chuyển sang **Archived Read-Only Mode** (mở xem tĩnh + Mini-quiz + Cache lỗi sai AI + đọc Q&A cũ; khóa quyền phát sinh chi phí; kèm rào chắn 1 phiên/1 luồng + **15h video/tháng** và **Dormant 24 tháng**). | 🔧 (đã có ghi danh & `readOnly` cơ bản; 📝 Access Period, Archived đầy đủ, vá bug rò rỉ BE/FE, 2 rào chắn) |
| S5 | Học nội dung, Sequential (AND) & Fast-track | Xem HLS 360p/720p (+ phụ đề `.vtt` tùy chọn, Dynamic Watermark). Ở chế độ `SEQUENTIAL` (mặc định): mở bài sau khi đạt **AND (≥ 80% video đo từ Heartbeat 30s + anti-seek + xác nhận đọc tài liệu VÀ ≥ 70% Mini-quiz)**. Đạt **≥ 85% Pre-test** theo LO (hoặc Module nếu không gắn LO) được **Fast-track** miễn 80% video (không thay Post-test). | 🔧 (đã có HLS + AND ở BE; 📝 vá FE `LessonItem`/`LessonView`, Heartbeat 30s, đọc tài liệu, Fast-track) |
| S6 | Làm 4 loại Quiz & Kỳ thi Đồng loạt 500 CCU | Đề chốt snapshot; hỗ trợ 4 loại `PRE_TEST`, `POST_TEST` (+ Remediation 100% video Module & Cooldown 24h), `MINI_QUIZ` (`Unlimited`, gợi ý Socratic `< 70%`), `MASS_EXAM` (**IndexedDB + Redis Delta Sync 20s/5s**, Hard Cutoff `T_end`, Client Jitter 0–10s, Grace 30s, Auto-Finalize, nộp qua Queue). | 🔧 (đã có snapshot & tự chấm cơ bản; 📝 4 loại Quiz & Mass Exam engine) |
| S7 | Nộp bài tập tự luận & Nộp lại (`Resubmit`) | Nộp Rich-text/Markdown và/hoặc file PDF/DOCX ≤ 20 MB (≥ 50 từ / 300 ký tự text-searchable); trong `max_submissions` và trước hạn được nộp lại (kể cả sau khi `GRADED` → `PENDING_REGRADE`), **bị chặn nộp lại khi bài đang `CLAIMED`**. | 🔧 (đã có nộp text/file & `PENDING_REGRADE`; 📝 MD editor, lọc PDF/DOCX text-searchable, chặn khi `CLAIMED`) |
| S8 | Xem tiến độ học tập & Bản đồ thành thạo LO | Theo từng lesson/module/enrollment (`✅`); bổ sung **Bản đồ thành thạo LO** khi khóa có gắn LO (`📝`) | 🔧 |
| S9 | Hỏi đáp trong khóa học (gắn mốc giây video) | Kênh chung khóa học **và** kênh riêng từng bài học (`lesson_id`, đính kèm file), bổ sung gắn **mốc giây video** (`📝`), chỉ enrollment còn quyền chủ động mới đăng mới được (Archived chỉ đọc) | 🔧 (vá chặn đăng khi `CoursePhase.ENDED` / hết `access_expires_at`; 📝 mốc giây video) |
| S10 | Vào danh sách chờ khi khóa đầy sĩ số *(Center Ops)* | Ghi danh ở trạng thái `WAITLISTED`, tự thăng `ACTIVE` theo FIFO khi có chỗ trống | ✅ |
| S11 | Khảo sát CSAT/NPS & Chứng chỉ Open Badges 3.0 | Hoàn thành khảo sát **CSAT/NPS** trước khi nhận chứng chỉ số chuẩn **Open Badges 3.0** (JSON-LD + chữ ký + QR/URL verify công khai) | 🔧 (đã có trang in & verify URL; 📝 CSAT/NPS, Open Badges 3.0 + QR) |
| S12 | Quyền dữ liệu cá nhân (Luật 91/2025 & NĐ 356/2025) | Tự xuất dữ liệu cá nhân dạng JSON (`GET /api/users/me/export` `✅`); bổ sung luồng **tự gửi yêu cầu khóa/xóa tài khoản** (`📝`) | 🔧 |

### 2.3. Giáo viên (`INSTRUCTOR`)

| # | Nghiệp vụ | Quy tắc (Đồng bộ BRD v1.4) | Trạng thái |
|---|---|---|---|
| T1 | Đăng nhập bằng tài khoản Admin cấp | Không có đăng ký tự do cho giáo viên | ✅ |
| T2 | Bắt buộc đổi mật khẩu lần đăng nhập đầu | `must_change_password=true` chặn truy cập API khác tại gateway tới khi đổi xong | ✅ |
| T3 | Xem các khóa học được giao | Chỉ các khóa đã được Admin gán (`course_instructors`) | ✅ |
| T4 | Xem nội dung khóa học — **chỉ đọc** & Flag Issue | Không có quyền sửa module/lesson/content/quiz/assignment/Rubric — khóa ở API; bổ sung nút **Flag Content/Rubric Issue** báo Admin khi phát hiện sai sót | ✅ (📝 Flag Issue) |
| T5 | Chấm bài tự luận (Claim/Lease, Rubric, Blind) & Phúc khảo | Nhận chấm **Claim/Lock 30p (Auto-extend Lease + `lock_token` + `@Version`)**, chấm theo **Rubric** ở chế độ **Blind Grading đồng nhất** (cả bài tập, câu tự luận quiz và phúc khảo). Phúc khảo (1 lần/7 ngày cho cả tự luận & quiz) định tuyến cho **GV thứ 2 cùng môn**; cảnh báo Admin nếu lệch > 15%. | 🔧 (đã có Blind cơ bản, `@Version`, 1x/7d; 📝 Claim/30p, Rubric, vá lộ tên khi phúc khảo/quiz essay, cảnh báo > 15%) |
| T6 | Trả lời Q&A, Thông báo khóa học & Sửa điểm Quiz có giới hạn | Trả lời Q&A khóa/bài học, đăng **Thông báo khóa học (`Announcement`)**. Sửa điểm lượt quiz chỉ áp dụng cho câu tự luận trong quiz hoặc đính chính lỗi đề/kỹ thuật (bắt buộc lý do + `AuditLog` + thông báo học viên; **không** sửa trắc nghiệm kỳ thi đồng loạt). | 🔧 (đã có Q&A/Announcement/sửa điểm; 📝 khóa sửa điểm trắc nghiệm thi đồng loạt) |

### 2.4. Quy tắc học tập & chấm điểm (bổ sung)

| Nghiệp vụ | Quy tắc (Đồng bộ BRD v1.4) |
|---|---|
| Điểm tổng kết & Hoàn thành khóa | Hoàn thành 100% Module bắt buộc + điểm trung bình trọng số các bài kiểm tra chính thức (`Post-test + Kỳ thi đồng loạt + Tự luận bắt buộc`, `weight_% = 100%`; không lấy Pre-test thay thế) đạt **≥ 70%** + hoàn thành khảo sát **CSAT/NPS** → tự động cấp chứng chỉ chuẩn **1EdTech Open Badges 3.0** (JSON-LD + chữ ký mật mã + QR/URL verify công khai). Mục được Admin miễn trừ (`Waiver`) được loại khỏi phép tính và điều kiện chặn. |
| Ai chấm điểm | **Chỉ giáo viên được phân công khóa (hoặc GV thứ 2 cùng môn khi phúc khảo)** chấm bài tự luận, câu tự luận trong quiz và xử lý phúc khảo. **Admin thường tuyệt đối không chấm** — chỉ xem theo dõi hàng đợi và bảng điểm. **Ngoại lệ duy nhất:** chỉ **Root Admin** được phép xử lý phúc khảo cuối cùng (kèm `AuditLog` mức cao) nếu môn học chỉ còn duy nhất 01 GV khả dụng (chính người đã chấm ban đầu). |
| Phúc khảo | Học viên gửi yêu cầu cho bài tự luận (`ASSIGNMENT_SUBMISSION`) hoặc lượt quiz (`QUIZ_ATTEMPT`) tối đa **01 lần/mục trong 07 ngày** kể từ khi công bố điểm; chấm ẩn danh hoàn toàn; điểm phúc khảo là cuối cùng; ghi log & cảnh báo Admin nếu độ lệch > 15%. |
| Câu hỏi tự luận | Do Admin soạn; học viên viết; giáo viên chấm ẩn danh và nhận xét; học viên xem nhận xét ở lịch sử quiz. |
| Quiz ngẫu nhiên | Rút N câu ngẫu nhiên theo LO (nếu khóa có gắn LO) hoặc Pool Lesson/Module (nếu không gắn LO), trộn câu/đáp án, snapshot cố định theo lượt làm. |
| Thời hạn khóa & cá nhân | Kết hợp linh hoạt theo cấu hình khóa: `startAt/endAt` (theo đợt), `access_period_days` (theo cá nhân) hoặc cả hai (`access_expires_at = min(...)`); gia hạn cá nhân qua Extension Code. Hết hạn chuyển sang **Archived Read-Only Mode** (xem tĩnh + Mini-quiz + Cache lỗi sai AI + 2 rào chắn Fair-Use 15h/tháng & Dormant 24 tháng). |
| Miễn trừ (`Waiver`) *(Center Ops)* | Admin miễn trừ quiz/bài tập cho từng học viên (bắt buộc ghi lý do + `AuditLog`) để mục quá hạn/bất khả kháng không chặn hoàn thành khóa (`✅`). |
| Thông báo & Email DLQ | 4 nhóm tùy chọn in-app/email (`✅`); thông báo hệ thống & chứng chỉ luôn gửi. Đẩy qua Transactional Outbox → RabbitMQ; sửa `EmailSender` để không nuốt lỗi, bật SMTP theo biến môi trường và thực hiện **Retry 3 lần + DLQ** (`📝`). |

## 3. Quy tắc nghiệp vụ chung (áp dụng toàn hệ thống)

1. **Không hard-delete dữ liệu nghiệp vụ.** Mọi "xoá" là đổi trạng thái (`DISABLED`, `ARCHIVED`, `WITHDRAWN`, `DORMANT`, `is_active=false`, ...). Ngoại lệ: thực thể con chưa gắn dữ liệu phụ thuộc vẫn có thể xoá cứng, nhưng bị chặn (409) nếu đã có dữ liệu con. **Riêng tài khoản Root Admin không bao giờ bị xóa/vô hiệu hóa/ẩn danh hóa.**
2. **Đường vào khóa học qua mã ghi danh (mã mời theo lớp / mã kích hoạt 1 lần).** Không có flow "xin duyệt", không có khóa học "riêng tư" ẩn khỏi danh sách. Ngoại lệ có kiểm soát: Admin ghi danh hàng loạt (`Bulk Enroll`, A15) vẫn gắn chặt vào 1 mã của khóa.
3. **Admin tạo được tài khoản cả 3 vai trò** (chỉ **Root Admin** được tạo/khóa `ADMIN`; không có đăng ký tự do cho `INSTRUCTOR`/`ADMIN`).
4. **Giáo viên không có quyền ghi** lên cấu trúc/nội dung khóa học (`module/lesson/content/quiz/assignment/rubric`) — chỉ Admin. Giáo viên ghi vào: điểm chấm & nhận xét bài tập/câu tự luận, phúc khảo, Q&A, Thông báo khóa học (`Announcement`), điểm danh buổi học (nếu khóa có buổi học) và báo lỗi (`Flag Content/Rubric Issue`).
5. **Không có cấp bậc giữa giáo viên** được gán cùng một khóa học.
6. **Admin thường không chấm bài/phúc khảo** — chỉ Root Admin có nhánh fallback ngoại lệ cuối cùng kèm `AuditLog` mức cao khi môn học chỉ còn 01 GV duy nhất.
7. **Mọi khóa đã `PUBLISHED` đều hiện giới thiệu công khai** — không có khái niệm "ẩn hẳn khỏi danh sách".

## 4. Trợ lý AI Tutor (Đồng bộ BRD v1.4 — Mục 3.4 & FR-08)

* **Đã triển khai (`✅`):** Document RAG Q&A 4 định dạng (`TEXT`, `PDF`, `MD`, `JSON`), lọc quyền theo bài đã mở khóa (`lesson_scope_sha256`), trích dẫn trang/heading/path, tự từ chối khi ngoài nguồn (`ABSTAINED`), đồng bộ outbox + invalidation khi Admin sửa/xóa tài liệu, kiến trúc adapter linh hoạt (`Groq`/`Gemini`/`OpenAI` & `Jina`/`OpenAI`/`Gemini`), ngữ cảnh **3 lượt (6 tin nhắn)**, khóa AI trong cùng khóa học khi có quiz `IN_PROGRESS`.
* **Kế hoạch bổ sung MVP (`📝`):** Prompt Socratic (không giải hộ), parser `.docx` & `.vtt` (tùy chọn) + One-Click Timestamp (`±90s`), khóa AI xuyên khóa theo `user_id` cho `PRE_TEST`/`POST_TEST`/`MASS_EXAM` (mở cho `MINI_QUIZ`), quota **20 lượt/ngày**, giới hạn câu hỏi **≤ 1.000 ký tự**, **Cached Mistake Explanation** (nạp sẵn + lazy-cache, mở cho cả Archived Read-Only), XML Delimiters + TTFT < 3s / Hard Timeout 15–20s + Circuit Breaker + Retrieval-only Fallback (Top-3), Eval Pipeline P5 & lưu Token FinOps.
* **Stretch (`STRETCH`):** AI Quiz Generator (Admin duyệt), AI Viva Voce, AI Pre-Grading, Semantic FAQ Deflection, Verified Q&A → RAG, FSRS Spaced Repetition. *(Ngân hàng câu hỏi thủ công + Import/Export CSV/JSON vẫn là `✅` trong MVP.)*

## 5. Kiến trúc, Hạ tầng Cloud & Phân tách Stretch (Đồng bộ BRD v1.4 — Mục 7–8 & Q11)

* **Kiến trúc thực tế (`✅`):** Modular Service-Oriented (`api-gateway`, `core-lms-service`, `assessment-service`, `worker-service`, Python `ai-tutor` + 2 workers); **PostgreSQL 16 (Primary + Read Replica)** cho LMS + **`ai-postgres` PG17 `pgvector` (`ADR-0015`)**; hạ tầng **AWS Terraform** (`ec2-demo` & `ecs-managed`, CI/CD GitHub Actions).
* **Đang nghiên cứu (`TBD`):** Bảo vệ luồng phát HLS tại Edge theo Phương án A (Thuần AWS CloudFront OAC + Signed Cookies) vs. Phương án B (Hybrid AWS Compute + Cloudflare R2 \$0 Egress + Worker HMAC).
* **Kế hoạch bổ sung hạ tầng & UX (`📝`):** Đưa `ai-tutor` + `ai-postgres` vào `docker-compose.prod.yml`/Terraform/CI-CD; Scheduled Pre-warming trước ca thi 15p; Observability AWS & siết alert `p95 < 300ms`; Drop-off từng bài, Learning Gain, LO Map, FinOps AI; phím tắt WCAG 2.1 AA cho trình làm quiz.
* **Stretch (`STRETCH`):** Song ngữ Tiếng Anh (i18n — Tiếng Việt là mặc định `✅`), Rewind Heatmap & Misconception Radar, đăng nhập Google OAuth.

Chi tiết đặc tả đầy đủ, bảng FinOps và sơ đồ kiến trúc: xem [BRD v1.4](../docs/BRD_Project8_OEP_v1.4.md) và [STATUS.md](STATUS.md).
