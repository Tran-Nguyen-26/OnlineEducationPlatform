# **TÀI LIỆU YÊU CẦU NGHIỆP VỤ (BRD) — Phiên bản 1.4 (Bản Đồng Bộ Thực Tế & Lộ Trình Bổ Sung MVP)**

**Đề tài:** Project 8 — Nền tảng Học trực tuyến và Luyện thi tích hợp AI (Online Education Platform - OEP)

**Học phần:** Cloud Application Development (Giai đoạn P1–P5 — Business, Data, Application, Security/DevOps, Production & AI Engineering)

**Tài liệu gốc & đối chiếu song hành:** Kế thừa từ [BRD_Project8_OEP.md](BRD_Project8_OEP.md) (v1.3) và đồng bộ trực tiếp với [BUSINESS.md](../backend/BUSINESS.md) cùng mã nguồn hiện tại trên nhánh `docs/unify-docs` (tách từ `dev-2`).

**Quy mô hướng tới:** Production-oriented MVP (1.000 – 2.000 học viên · 5–7 sinh viên · ca thi đỉnh 500 CCU)

**Ngày cập nhật:** 27/11/2026 *(Bản chốt hoàn chỉnh Mục 1–8 & Phụ lục A, đã đồng bộ 100% với [BUSINESS.md](../backend/BUSINESS.md))*

**Quy ước đánh dấu trạng thái trong v1.4:**
* **`[ĐÃ TRIỂN KHAI]`** = Tính năng/quy tắc đã có trong mã nguồn (`backend/` / `frontend/` / `ai-tutor/` / `infra/`) và khớp với v1.4.
* **`[ĐÃ TRIỂN KHAI - CẦN VÁ LỖI]`** = Đã có mã nguồn nền tảng nhưng còn lỗi lệch nội bộ FE–BE hoặc lỗ hổng thực thi cần vá.
* **`[KẾ HOẠCH BỔ SUNG - MVP]`** = Yêu cầu bắt buộc của MVP đã chốt trong v1.4, sẽ tiếp tục lập trình bổ sung trước khi đóng dự án.
* **`[ĐANG NGHIÊN CỨU - TBD]`** = Có phương án kiến trúc đang cân nhắc để chọn giải pháp tối ưu.
* **`[STRETCH]`** = Tính năng mở rộng (Stretch Goal), triển khai sau khi hoàn tất Core MVP.
* **`[QĐ]`** = Quyết định nghiệp vụ/kiến trúc đã được chốt chính thức.

---

# **1. TỔNG QUAN DỰ ÁN & ĐỊNH VỊ OEP**

## **1.1. Bối cảnh & Bài toán Nghiệp vụ**

Đối tác là một trung tâm đào tạo sở hữu giáo trình chuẩn hóa nội bộ (nhiều Môn học `Subject`, mỗi môn nhiều Khóa học `Course`), quy mô **1.000 – 2.000 học viên đang hoạt động** (mỗi khóa **300 – 500 học viên**). Học viên học trực tuyến theo lộ trình Mastery Learning (mặc định tuần tự `SEQUENTIAL`, hỗ trợ tùy chọn tự do `FREE_NAVIGATION` và tùy chọn lịch buổi học live/hỗ trợ theo từng khóa), làm bài đánh giá định kỳ và được trợ lý AI hỗ trợ bám sát học liệu. Hệ thống phục vụ **một đơn vị sở hữu duy nhất (Closed-Cohort B2B2C / Single-Tenant LMS)**, không phải chợ khóa học thương mại tự do (Open Marketplace).

## **1.2. Đối chiếu Tiêu chuẩn Tối thiểu của một OEP (Bản chốt hoàn chỉnh v1.4)**

| Nhóm tiêu chuẩn | Yêu cầu cốt lõi | Thiết kế áp dụng trong MVP v1.4 (Đã chốt `[QĐ]`) | Trạng thái |
| :--- | :--- | :--- | :--- |
| **1. Người dùng & Vai trò** | ≥ 3 vai trò, xác thực + RBAC | Đúng **3 vai trò RBAC** (`STUDENT`, `INSTRUCTOR`, `ADMIN`) kèm cờ hệ thống `is_root_admin = true` cho **01 tài khoản Root Admin duy nhất** (không thể xóa/vô hiệu hóa/ẩn danh hóa). Học viên tự đăng ký (`PENDING_VERIFICATION` + xác thực Email OTP/Link + Explicit Consent); Admin tạo được cả 3 vai trò (riêng tạo/khóa `ADMIN` chỉ Root Admin được phép). JWT RS256 + JWKS + Refresh Token + RBAC 100% API. | `[ĐÃ TRIỂN KHAI]` nền Auth/RBAC; `[KẾ HOẠCH BỔ SUNG - MVP]` Email verify & Root Admin |
| **2. Quản lý nội dung** | Course → Module → Lesson; Video, tài liệu, Quiz | **Chỉ Admin** thiết kế/nhập cấu trúc `Subject → Course → Module → Lesson → LessonContent`, gắn **LO & Bloom (tùy chọn)**, nạp học liệu (`.mp4 ≤ 2 GB`, `.vtt/.srt ≤ 5 MB` tùy chọn, `PDF/DOCX ≤ 20 MB`), đóng gói HLS **360p/720p**, quản lý Ngân hàng câu hỏi (thủ công + CSV/JSON), cấu hình Rubric & tỷ trọng điểm (`weight_% = 100%`), Publish Validation 2 lớp. | `[ĐÃ TRIỂN KHAI]` khung nội dung/HLS 360p-720p/CSV-JSON bank; `[KẾ HOẠCH BỔ SUNG - MVP]` LO tùy chọn, `.vtt`, Publish 2 lớp, Rubric |
| **3. Học tập & Ghi danh** | Ghi danh, truy cập từ xa, theo dõi tiến độ | Hỗ trợ **2 chế độ mã** (Mã mời theo lớp `max_uses > 1` & Mã kích hoạt 1 lần `max_uses = 1` kèm lô CSV/QR & Mã gia hạn); thời hạn học linh hoạt theo **đợt khóa (`startAt/endAt`)**, theo **cá nhân (`access_period_days` 90/180 ngày)** hoặc **kết hợp cả 2** (`access_expires_at`); hết hạn chuyển **Archived Read-Only Mode** kèm 2 rào chắn (1 luồng + 15h/tháng; Dormant 24 tháng). Mặc định `SEQUENTIAL` (toán tử AND) + Fast-track Pre-test ≥ 85%. | `[ĐÃ TRIỂN KHAI]` mã mời + `CoursePhase` + Sequential cơ bản; `[KẾ HOẠCH BỔ SUNG - MVP]` lô CSV/QR, Access Period, Archived + 2 rào chắn, Fast-track, Heartbeat 30s |
| **4. Đánh giá & Khảo thí** | Quiz, bài tập, chấm điểm, phản hồi | 4 loại Quiz (`PRE_TEST`, `POST_TEST` + Remediation 24h, `MINI_QUIZ` Unlimited + Socratic hint, `MASS_EXAM` 500 CCU với Hard Cutoff + IndexedDB/Redis 20s/5s + Grace 30s + Auto-Finalize + Queue + Pre-warming + Exam Panic Mode); Tự luận Rich-text/PDF/DOCX text-searchable ≥ 50 từ, Blind Grading đồng nhất + Rubric + Claim/Lock 30p, Resubmit (`PENDING_REGRADE`), Phúc khảo 1 lần/7 ngày (cả tự luận & quiz) cho GV thứ 2 cùng môn (fallback Root Admin) + cảnh báo lệch > 15%. | `[ĐÃ TRIỂN KHAI]` Quiz cơ bản, Blind Grading, `@Version`, Phúc khảo 1x/7d; `[KẾ HOẠCH BỔ SUNG - MVP]` 4 loại Quiz, Mass Exam engine, Rubric, Claim/Lease, PDF text-check, cảnh báo > 15% |
| **5. Tương tác & Thông báo** | ≥ 1 kênh thảo luận + thông báo bất đồng bộ | Q&A theo khóa học và theo từng bài học (`lesson_id`, đính kèm file, gắn mốc giây video) + Thông báo khóa học (`Announcement`) của GV + nút **Flag Content/Rubric Issue** báo Admin. Thông báo In-app & Email bất đồng bộ qua Outbox → RabbitMQ + Retry 3 lần + DLQ. | `[ĐÃ TRIỂN KHAI]` Q&A, Announcements, Outbox+DLQ In-app; `[KẾ HOẠCH BỔ SUNG - MVP]` mốc giây Q&A, Flag Issue, Email Retry 3x + DLQ |
| **6. Chứng nhận & Analytics** | Xác nhận hoàn thành, chứng chỉ số, báo cáo | Tự động xét hoàn thành (100% Module + điểm trung bình trọng số bài thi chính thức `weight_% = 100%`, không tính Pre-test, đạt ≥ 70%) + khảo sát **CSAT/NPS** → xuất **Digital Certificate chuẩn 1EdTech Open Badges 3.0** (JSON-LD + chữ ký mật mã + QR/URL verify công khai). Dashboard Completion, Drop-off theo từng bài, Learning Gain (`Post − Pre`), Bản đồ LO (khi khóa có LO), FinOps Token AI. | `[ĐÃ TRIỂN KHAI]` tự hoàn thành ≥ 70%, verify URL, báo cáo cấp khóa; `[KẾ HOẠCH BỔ SUNG - MVP]` Open Badges 3.0 + QR, CSAT/NPS, Drop-off theo bài, Learning Gain, LO Map, FinOps AI |
| **7. Trợ lý AI & Đánh giá P5** | RAG bám học liệu, chống gian lận, Eval P5 | Document RAG 4 định dạng (`TEXT/PDF/MD/JSON`) lọc chặt theo bài đã mở khóa + Abstention `[ĐÃ TRIỂN KHAI]`; nâng cấp Prompt Socratic, `.docx`/`.vtt` tùy chọn + One-Click Timestamp (`±90s`), khóa AI xuyên khóa khi thi (mở cho Mini-quiz), quota 20 lượt/ngày, câu hỏi **≤ 1.000 ký tự**, ngữ cảnh 3 lượt (6 tin nhắn), Cached Mistake Explanation, XML Delimiters + Hard Timeout 15–20s + Circuit Breaker + Retrieval-only Fallback (Top-3), Eval P5 & lưu Token FinOps `[KẾ HOẠCH BỔ SUNG - MVP]`. AI Quiz Generator chuyển sang `[STRETCH]`. | `[ĐÃ TRIỂN KHAI]` nền Document RAG; `[KẾ HOẠCH BỔ SUNG - MVP]` Socratic, Timestamp, Mistake Cache, Quota, Fallback, Eval P5 |
| **8. Vận hành Trung tâm (Center Operations)** | Quản lý vận hành thực tế cho trung tâm đào tạo | **Out-of-Scope cổng thanh toán trực tuyến & không lưu thẻ/ngân hàng.** Hệ thống bổ sung 05 tính năng vận hành trung tâm bổ trợ đã triển khai: (1) Sổ cái học phí thu tay/công nợ/hoàn tiền/miễn giảm/biên lai; (2) Lịch học theo buổi & Điểm danh (tùy chọn theo khóa); (3) Danh sách chờ (`WAITLISTED`) FIFO; (4) Điều kiện tiên quyết khóa học (BFS chống vòng lặp); (5) Ghi danh hàng loạt email (`Bulk Enroll`) & Miễn trừ mục đánh giá (`Waiver`). | `[ĐÃ TRIỂN KHAI]` |

---

# **2. CÁC BÊN LIÊN QUAN & PHÂN QUYỀN (RBAC) `[QĐ]`**

Hệ thống giữ đúng **03 vai trò RBAC** (`STUDENT`, `INSTRUCTOR`, `ADMIN`). Riêng vai trò `ADMIN` có thêm cờ hệ thống **`is_root_admin = true`** gắn duy nhất cho **01 tài khoản Admin gốc (Root Admin)** khởi tạo lúc thiết lập hệ thống: tài khoản này **không thể bị xóa, vô hiệu hóa (`DISABLED`), hạ quyền hay ẩn danh hóa**.

| Vai trò | Quy mô | Quyền hạn & Trách nhiệm (Bản chốt v1.4 `[QĐ]`) |
| :--- | :--- | :--- |
| **1. Học viên (`STUDENT`)** | 1.000 – 2.000 (300 – 500/khóa) | • **Tự đăng ký tài khoản** (`[ĐÃ TRIỂN KHAI]`) ở trạng thái `PENDING_VERIFICATION`, bắt buộc **xác thực Email qua OTP/Link** (hiệu lực 15–30 phút, có rate-limit gửi lại) (`[KẾ HOẠCH BỔ SUNG - MVP]`) và xác nhận **Explicit Consent** (`[ĐÃ TRIỂN KHAI]`). Tài khoản do Admin tạo hộ được miễn bước xác thực email nhưng **bắt buộc đổi mật khẩu lần đăng nhập đầu** (`must_change_password = true`).<br>• Nhập **Mã kích hoạt / Mã mời** để ghi danh khóa học; học Video HLS (360p/720p), tài liệu, làm Pre/Post-test, Mini-quiz, Kỳ thi đồng loạt, nộp bài tự luận (Rich-text/Markdown hoặc PDF/DOCX text-searchable ≤ 20 MB), gửi phúc khảo (1 lần/7 ngày), tham gia Q&A và hỏi AI Tutor.<br>• Chịu kiểm soát **tối đa 3 thiết bị**, **1 luồng phát video hoạt động (Heartbeat 30s)** và **Single-Device Exam Lock** (`[KẾ HOẠCH BỔ SUNG - MVP]`). |
| **2. Giảng viên (`INSTRUCTOR`)** | 15 – 30 (Do Admin cấp nội bộ) | • Đăng nhập bằng tài khoản Admin cấp, bắt buộc đổi mật khẩu lần đầu (`[ĐÃ TRIỂN KHAI]`).<br>• **Nhận (Claim/Lock 30p) và chấm** bài tự luận từ Hàng đợi chấm chung theo **Rubric ẩn danh (Blind Grading)** (`[KẾ HOẠCH BỔ SUNG - MVP]` cho Claim/Rubric; `[ĐÃ TRIỂN KHAI]` khung Blind Grading); chấm phúc khảo độc lập khi được định tuyến là GV thứ 2 cùng môn.<br>• Chấm câu hỏi tự luận trong quiz và được phép **điều chỉnh điểm lượt quiz** (`PATCH /api/attempts/{id}/score`) nhưng **giới hạn chặt phạm vi**: chỉ dùng khi xử lý câu tự luận trong quiz hoặc đính chính lỗi đề/lỗi kỹ thuật (bắt buộc nhập lý do + ghi `AuditLog` + gửi thông báo học viên; **tuyệt đối không** cho tự ý sửa điểm trắc nghiệm kỳ thi đồng loạt) (`[ĐÃ TRIỂN KHAI - CẦN VÁ LỖI]` siết phạm vi).<br>• Giải đáp Q&A khóa/bài học, **đăng Thông báo khóa học (`Announcement`)** (`[ĐÃ TRIỂN KHAI]`), xem danh sách lớp/bảng điểm/Dashboard Analytics và bấm **Flag Content/Rubric Issue** gửi Admin khi phát hiện sai sót học liệu/đề/Rubric (`[KẾ HOẠCH BỔ SUNG - MVP]`).<br>• **Không** tạo/sửa cấu trúc khóa học, LO, học liệu gốc, Rubric hay duyệt Ngân hàng câu hỏi (`[ĐÃ TRIỂN KHAI]` khóa chặt ở API `/api/admin/**`). |
| **3. Quản trị viên (`ADMIN` & Root Admin)** | 3 – 5 nhân sự học vụ | • **Quản lý tài khoản cả 3 vai trò (`ADMIN`, `INSTRUCTOR`, `STUDENT`):** **Chỉ Root Admin** (`is_root_admin = true`) mới có quyền tạo/vô hiệu hóa tài khoản `ADMIN` khác; Admin thường quản lý `INSTRUCTOR` và `STUDENT` (tạo mới kèm mật khẩu tạm bắt đổi lần đầu, bật/tắt `ACTIVE`/`DISABLED`, tạo link đặt lại mật khẩu) (`[ĐÃ TRIỂN KHAI]` tạo GV/HV; `[KẾ HOẠCH BỔ SUNG - MVP]` tạo `ADMIN` & khóa bảo vệ Root Admin).<br>• Thiết kế `Subject → Course → Module → Lesson`, gắn LO & Bloom (tùy chọn), tải học liệu, cấu hình Rubric, cấu hình tỷ trọng điểm (`weight_% = 100%`), cấu hình `max_attempts`, quản lý Ngân hàng câu hỏi (thủ công + CSV/JSON `[ĐÃ TRIỂN KHAI]`, AI Quiz Generator `[STRETCH]`), Publish khóa học.<br>• Quản lý vòng đời Mã kích hoạt / Mã mời (tạo lẻ `[ĐÃ TRIỂN KHAI]`, tạo lô CSV/QR, thu hồi, cấp lại, Mã gia hạn `[KẾ HOẠCH BỔ SUNG - MVP]`); lên lịch Kỳ thi đồng loạt; vận hành 05 nhóm nghiệp vụ trung tâm bổ trợ (sổ cái học phí thu tay, lịch học/điểm danh, waitlist, tiên quyết, bulk enroll & waiver `[ĐÃ TRIỂN KHAI]`); theo dõi Audit Log, Ops/DLQ, báo cáo & cảnh báo lệch điểm phúc khảo > 15%.<br>• **Nguyên tắc Admin không chấm:** **Admin thường tuyệt đối không chấm bài/phúc khảo** (chỉ xem theo dõi hàng đợi & bảng điểm `[ĐÃ TRIỂN KHAI]`). **Chỉ Root Admin** có quyền xử lý phúc khảo ngoại lệ cuối cùng kèm `AuditLog` mức cao khi môn học chỉ còn duy nhất 01 GV khả dụng (chính người đã chấm ban đầu) (`[KẾ HOẠCH BỔ SUNG - MVP]` siết quyền fallback từ mọi Admin về riêng Root Admin). |

---

# **3. LUỒNG NGHIỆP VỤ CỐT LÕI**

## **3.1. Thiết kế Khóa học, Chuẩn đầu ra (LO) Tùy chọn & Xử lý Học liệu `[QĐ]`**

1. **Cấu trúc chuẩn:** `Subject → Course → Module → Lesson → LessonContent` (`[ĐÃ TRIỂN KHAI]`).
2. **Chuẩn đầu ra (LO) & Thang nhận thức Bloom (Tùy chọn gắn — `[KẾ HOẠCH BỔ SUNG - MVP]`):**
   * Hệ thống hỗ trợ Admin khai báo **Learning Outcome (LO)** cho khóa học và gắn mã LO + mức nhận thức thang Bloom rút gọn (**Nhớ / Hiểu / Vận dụng**) lên Module/Lesson, học liệu, Quiz, Assignment và câu hỏi Ngân hàng đề.
   * **Tính tùy chọn:** Gắn LO là **tùy chọn theo từng khóa học** (các khóa không dùng LO vẫn vận hành đầy đủ theo cấp Module/Lesson; nhánh `ai-tutor` truy xuất theo `lesson_id` đã mở khóa nên hoàn toàn tương thích cả hai trường hợp).
3. **Quy tắc kiểm định khi Phát hành khóa học (Publish Validation 2 lớp — `[KẾ HOẠCH BỔ SUNG - MVP]`):**
   * Hiện trạng `[ĐÃ TRIỂN KHAI]`: `AdminCourseService.updateStatus()` mới chỉ chặn khóa không có bài học (`>= 1 Lesson`).
   * Chuẩn v1.4 gồm **2 lớp kiểm định** trước khi chuyển `DRAFT → PUBLISHED`:
     * **Lớp 1 — Bắt buộc cho mọi khóa học:** (a) Có tối thiểu `01 Module`, `01 Lesson` và `01 học liệu`; (b) Tổng tỷ trọng điểm (`weight_%`) của các bài kiểm tra chính thức (`Post-test + Kỳ thi đồng loạt + Bài tự luận bắt buộc`) phải **bằng 100%**; (c) Nếu khóa có bài tập tự luận, khóa phải được phân công **tối thiểu 02 Giảng viên phụ trách** (hoặc trong cùng `Subject` có **≥ 02 Giảng viên đang `ACTIVE`**) để đảm bảo năng lực chấm phúc khảo độc lập.
     * **Lớp 2 — Áp dụng khi khóa học có khai báo LO:** Mọi LO đã khai báo phải có tối thiểu **01 học liệu**, tối thiểu **03 câu hỏi Mini-quiz** (để trộn đề khi làm lại) và tối thiểu **01 câu hỏi đánh giá thuộc bài kiểm tra chính thức**.
4. **Luồng tải Học liệu & Đóng gói Video HLS bất đồng bộ:**
   * **Upload trực tiếp qua Presigned URL & Xác thực dung lượng thật trên Object Storage (`[ĐÃ TRIỂN KHAI]`):** Admin tải `.mp4 ≤ 2 GB`, phụ đề `.vtt/.srt ≤ 5 MB` (**tùy chọn** — có thể đăng hoặc không đăng kèm video), tài liệu `PDF/DOCX/MD/JSON ≤ 20 MB` (riêng `.md`/`.json` cho AI ≤ 5 MiB) trực tiếp lên Object Storage; Backend kiểm tra kích thước/MIME thật của object sau khi upload và xóa ngay file vượt ngưỡng.
   * **Đóng gói HLS bất đồng bộ (`[ĐÃ TRIỂN KHAI]`):** Sự kiện upload hoàn tất đi qua Transactional Outbox → RabbitMQ (`oep.worker.video`) → `worker-service` dùng FFmpeg nén sang **HLS đa độ phân giải 360p / 720p** (`.m3u8` + `.ts`) giúp tối ưu băng thông cho người học mạng yếu.
   * **Phụ đề `.vtt` tùy chọn & Trích xuất RAG (`[KẾ HOẠCH BỔ SUNG - MVP]`):** Bổ sung slot upload `.vtt/.srt` gắn kèm video trên Admin UI, thẻ `<track>` hiển thị phụ đề đồng bộ trên `HlsVideo.tsx`, và mở rộng parser `.vtt` trong `ai-tutor` để bóc tách timestamped chunks (bài không có `.vtt` tiếp tục dùng 4 định dạng `TEXT/PDF/MD/JSON` `[ĐÃ TRIỂN KHAI]`).
   * **Bảo vệ luồng phát HLS & Dynamic Watermark:**
     * *Giai đoạn 1 (`[ĐÃ TRIỂN KHAI]`):* `HlsPlaylistService` xác thực JWT + quyền truy cập bài học rồi ký Presigned GET URL ngắn hạn (600s) cho từng segment `.ts` trong `.m3u8`.
     * *Giai đoạn nâng cấp (`[ĐANG NGHIÊN CỨU - TBD]` + `[KẾ HOẠCH BỔ SUNG - MVP]`):* Bổ sung **Dynamic Watermark** (Email/Mã học viên di chuyển nhẹ trên khung hình), gỡ bỏ link *"Mở trong tab mới"* làm lộ presigned URL video trên FE, và nâng cấp xác thực HLS tại CDN Edge theo **01 trong 02 phương án đang nghiên cứu** (sẽ chốt đồng bộ cùng Phần 7 Hạ tầng & FinOps):
       * **Phương án A (Thuần AWS):** Gắn S3 làm Origin `/media/*` trên **AWS CloudFront + OAC** và bảo vệ bằng **CloudFront Signed Cookies (RSA Key Group)** do Backend cấp (khớp 100% hạ tầng Terraform AWS đang triển khai).
       * **Phương án B (Hybrid Media Zero-Egress):** Giữ Compute/DB trên AWS, tách Object Storage media sang **Cloudflare R2 (\$0 Egress)** và bảo vệ bằng **Cloudflare Worker HLS Auth Proxy + WAF HMAC-SHA256 Signed Session Cookies**.

## **3.2. Tự Đăng ký, Ghi danh, Thời hạn học, Chế độ Xem tĩnh & Lộ trình Học `[QĐ]`**

1. **Đăng ký & 02 Chế độ Mã ghi danh:**
   * Học viên tự đăng ký (`PENDING_VERIFICATION` → xác thực Email OTP/Link + Explicit Consent) rồi nhập mã để ghi danh toàn khóa (hoặc được Admin cấp tài khoản hộ).
   * Hỗ trợ **02 chế độ mã** trong cùng hệ thống `course_invite_codes`:
     * **Mã mời theo lớp (`max_uses > 1` hoặc theo sĩ số khóa)** (`[ĐÃ TRIỂN KHAI]`): dùng cho các lớp nhập học tập trung theo đợt.
     * **Mã kích hoạt 1 lần (`max_uses = 1`, gắn `1 tài khoản – 1 khóa học`)** (`[ĐÃ TRIỂN KHAI]` cột `max_uses/used_count`; `[KẾ HOẠCH BỔ SUNG - MVP]` tạo lô **CSV/QR**, thu hồi, cấp lại, **Mã gia hạn `Extension Code`**, và cập nhật atomic SQL theo mã).
2. **Mô hình Thời hạn học kết hợp (Cohort Dates & Personal Access Period):**
   * Khi tạo/cấu hình khóa học, Admin có thể chọn **01 trong 02 cơ chế hoặc kết hợp cả hai**:
     1. **Theo đợt khóa học (`Course.startAt` / `Course.endAt` → `UPCOMING / ONGOING / ENDED`)** (`[ĐÃ TRIỂN KHAI]`): phù hợp các khóa mở theo Đợt 1, Đợt 2 dùng chung ngày khai giảng và kết thúc.
     2. **Theo thời hạn cá nhân (`Course.access_period_days`, mặc định 90 hoặc 180 ngày từ lúc kích hoạt mã)** (`[KẾ HOẠCH BỔ SUNG - MVP]`): phù hợp các khóa bổ trợ tự học mở quanh năm.
     3. **Kết hợp cả hai (`[KẾ HOẠCH BỔ SUNG - MVP]`):** Khi kích hoạt mã, hệ thống lưu mốc hết hạn cá nhân `enrollments.access_expires_at = min(activated_at + access_period_days, course.endAt)` (nếu chỉ cấu hình 1 trong 2 thì lấy mốc tương ứng). Khi Admin phát hành **Mã gia hạn (Extension Code)** cộng thêm `K` ngày cho một học viên, hệ thống cộng thẳng `K` ngày vào `enrollments.access_expires_at` của học viên đó (cho phép gia hạn cá nhân vượt qua `course.endAt` mà không ảnh hưởng cả lớp).
3. **Chế độ Lưu trữ Xem tĩnh (Archived Read-Only Mode) & 02 Rào chắn (`[KẾ HOẠCH BỔ SUNG - MVP]`):**
   * Sau khi vượt `access_expires_at` (hoặc khóa `ENDED` / học viên đã `COMPLETED`), tài khoản tự động chuyển sang **Archived Read-Only Mode** vĩnh viễn theo vòng đời khóa học:
     * **Quyền xem tĩnh được giữ:** Xem Video HLS, đọc tài liệu, **làm lại Mini-quiz tự luyện**, xem **lời giải thích lỗi sai AI đã có sẵn trong Cache (0 token mới)**, đọc các thảo luận Q&A cũ.
     * **Quyền phát sinh chi phí bị khóa:** Chat trực tiếp AI Tutor, nộp tự luận vào hàng đợi GV, tham gia Kỳ thi đồng loạt, làm Post-test lấy chứng chỉ, đăng Q&A mới. *(Vá ngay 2 lỗi hiện tại `[ĐÃ TRIỂN KHAI - CẦN VÁ LỖI]`: bổ sung kiểm tra `CoursePhase.ENDED` / `access_expires_at` tại `CourseMessageService` & `AiTutorAccessPolicy`, và khóa đúng các tab AI/Quiz/Assignment/Q&A trên sidebar `CourseViewerPage.tsx` khi `readOnly`.)*
     * **Rào chắn 1 — Fair-Use & Chống sang nhượng:** Tài khoản ở chế độ xem tĩnh bị giới hạn **1 phiên đăng nhập / 1 luồng phát đồng thời** và hạn mức **15 giờ phát video/tháng (54.000 giây)**.
     * **Rào chắn 2 — Chính sách Tài khoản Ngủ đông (Dormant Policy):** Không đăng nhập liên tục trong **24 tháng** → tự động xóa/ẩn danh hóa nhật ký hành vi (Heartbeat logs) theo Nghị định 356/2025/NĐ-CP và chuyển tài khoản sang trạng thái `DORMANT` (cần xác thực OTP qua email gốc để mở lại).
4. **Kiểm soát Thiết bị & Khóa cứng 01 Luồng Phát Video (`[KẾ HOẠCH BỔ SUNG - MVP]`):**
   * Trong thời hạn học chủ động: tối đa **03 thiết bị cá nhân** đăng nhập (thiết bị thứ 4 tự động thu hồi phiên thiết bị cũ nhất, lưu metadata thiết bị vào `refresh_tokens.device_info`).
   * Khóa cứng **01 luồng phát video HLS hoạt động đồng thời** tại một thời điểm thông qua **Redis Heartbeat mỗi 30 giây**.
   * **Single-Device Exam Lock:** Khi tài khoản đang có phiên làm bài `ACTIVE` trong Post-test hoặc Kỳ thi đồng loạt, chặn đăng nhập/mở đề trên thiết bị mới (trừ khi học viên xác nhận hủy phiên thiết bị cũ kèm ghi `Device Handover Audit Log`).
5. **Lộ trình Học — Sequential Mastery (Toán tử VÀ) & Fast-track qua Pre-test:**
   * **Chế độ học:** Hỗ trợ `SEQUENTIAL` và `FREE_NAVIGATION`; đặt **`SEQUENTIAL` làm mặc định** cho khóa học mới (`[KẾ HOẠCH BỔ SUNG - MVP]` đổi mặc định từ `FREE_NAVIGATION`).
   * **Quy tắc mở khóa bài sau (Toán tử VÀ / AND — `[KẾ HOẠCH BỔ SUNG - MVP]` + `[ĐÃ TRIỂN KHAI - CẦN VÁ LỖI]`):** Ở chế độ `SEQUENTIAL`, áp dụng mặc định điều kiện **AND**: hoàn thành **≥ 80% thời lượng video** (tính ở Backend từ tổng số **Heartbeat 30s hợp lệ** kết hợp anti-seek phía client, không tin con số `percent` đơn lẻ từ FE) **+ xác nhận đọc tài liệu VÀ đạt ≥ 70% điểm Mini-quiz cuối bài** (nếu bài có các thành phần tương ứng). *(Vá 2 lỗi lệch FE–BE: bỏ `disabled` ô `minVideoPercentage` tại `LessonItem.tsx` và sửa checklist `LessonView.tsx` kiểm tra điểm `≥ 70%` thay vì `attemptsUsed > 0`.)*
   * **Fast-track qua Pre-test (`[KẾ HOẠCH BỔ SUNG - MVP]`):** Học viên làm bài chẩn đoán đầu khóa (`PRE_TEST`) đạt **≥ 85%** (Admin cấu hình được) sẽ được miễn điều kiện xem 80% video lý thuyết cho các bài học thuộc **LO đạt chuẩn (nếu khóa có gắn LO)** hoặc **Module đạt chuẩn (nếu khóa không gắn LO)**. Điểm Pre-test **không** thay thế điểm Post-test / Kỳ thi chính thức khi xét chứng nhận.

## **3.3. Đánh giá, Kỳ thi Đồng loạt, Hàng đợi Chấm bài & Phúc khảo `[QĐ]`**

### **A. Phân loại 4 nhóm Quiz, Số lượt làm lại & Học bù (Remediation) (`[KẾ HOẠCH BỔ SUNG - MVP]`)**

Mở rộng bảng `quizzes` với trường phân loại **`quiz_type`** (`PRE_TEST`, `POST_TEST`, `MINI_QUIZ`, `MASS_EXAM`), hỗ trợ cấu hình `max_attempts`, cách lấy điểm (`HIGHEST` / `LATEST` / `AVERAGE`), và trộn/bốc đề ngẫu nhiên (`[ĐÃ TRIỂN KHAI]` bốc ngẫu nhiên phẳng; nâng cấp ưu tiên phân bổ theo LO nếu khóa có gắn LO, hoặc Pool của Lesson/Module nếu không gắn LO):

1. **`PRE_TEST` và `MASS_EXAM`:** Khóa cứng `max_attempts = 1` ở tầng Backend để bảo toàn chỉ số Learning Gain và chống làm thử đề.
2. **`POST_TEST` (Bài kiểm tra tính điểm xét chứng nhận):**
   * Admin đặt `max_attempts` (khuyến nghị 1–3). Khi `max_attempts > 1`, hệ thống **tự động ẩn đáp án đúng chi tiết và ẩn nút "AI giải thích lỗi sai"** cho đến khi học viên đã **Đạt (≥ 70%)** hoặc đã dùng hết lượt; mỗi lượt làm lại tự động trộn/bốc câu hỏi khác cùng LO/Module từ Ngân hàng câu hỏi.
   * **Cơ chế Học bù Mở lượt thi lại (`REMEDIATION_REQUIRED`):** Nếu học viên trượt hết `max_attempts` (`< 70%`) trong thời hạn học chủ động, hệ thống tự động cấp thêm **01 lượt thi lại bổ sung** sau khi học viên hoàn thành xem lại **100% thời lượng video của Module đó** và vượt qua thời gian chờ **Cooldown 24 giờ**.
3. **`MINI_QUIZ` (Tự luyện cuối Lesson):** Mặc định `max_attempts = UNLIMITED`. Khi nộp bài `< 70%`, chỉ hiển thị **gợi ý Socratic theo khái niệm** (không lộ đáp án chữ cái A/B/C/D) và trộn câu hỏi từ Pool (≥ 3 câu/LO nếu có LO); đạt ≥ 70% mở đáp án chi tiết & giải thích đầy đủ.

### **B. Kỳ thi Đồng loạt (Scheduled Mass Exam — 500 CCU) (`[KẾ HOẠCH BỔ SUNG - MVP]`)**

*(Ghi nhận hiện trạng `[ĐÃ TRIỂN KHAI]`: đã có baseline load test k6 300 học viên chấm đồng bộ trực tiếp trên Postgres trong `LOAD_TEST_REPORT.md` và khung hàng đợi `ScoreSyncListener`.)*

* **Đồng hồ máy chủ & Vào muộn:** Ca thi có giờ mở `T_start` và giờ đóng cố định `T_end` theo đồng hồ máy chủ; học viên vào muộn tối đa **15 phút** (Admin cấu hình được) chỉ làm bài trong thời gian còn lại tới `T_end`; quá 15 phút bị từ chối vào ca thi.
* **Hard Cutoff, Aggressive Sync, Grace Window & Auto-Finalize:**
  * **Hard Cutoff (`T_end`):** Đúng `T_end` theo đồng hồ máy chủ, giao diện khóa chọn đáp án; mọi thay đổi có `interaction_timestamp > T_end` đều bị từ chối.
  * **Lưu nháp 2 lớp & Đồng bộ gia tốc phút cuối:** Ghi tức thời tại **IndexedDB** trình duyệt (thay thế `localStorage` hiện tại); đồng bộ **Dirty-state Delta Sync** bất đồng bộ lên **Redis** mỗi **20 giây**, và tự động rút xuống **5 giây/lần trong 60 giây cuối trước `T_end`** (End-of-Exam Aggressive Sync).
  * **Cửa sổ ân hạn truyền tải (`T_end + 30 giây`) & Client Jitter (`0–10 giây`):** Máy chủ chấp nhận gói `FINAL_SUBMIT` (được rải ngẫu nhiên 0–10s từ client) trong `[T_end, T_end + 30s]` miễn các đáp án ghi nhận `<= T_end`.
  * **Server-side Auto-Finalize Fallback:** Sau `T_end + 30s`, nếu client rớt mạng hoàn toàn không gửi được `FINAL_SUBMIT`, Worker máy chủ (`W01`) tự động chấm từ bản nháp mới nhất trong **Redis** (0% mất bài).
* **Nộp bài bất đồng bộ qua Message Queue (Shock Absorber):** Yêu cầu nộp bài đẩy vào hàng đợi ưu tiên (`exam-submission-queue`) để Worker chấm & ghi DB; học viên nhận biên nhận ngay (*"Đã ghi nhận bài nộp lúc HH:MM:SS"*). Đáp án đúng tuyệt đối không trả về client trước khi đóng ca thi.
* **Cảnh báo quá tải, Pre-warming & Exam Panic Mode (Circuit Breaker):**
  * Cảnh báo Admin khi ca thi dự kiến > 500 CCU và tự động **Pre-warming** trước giờ mở đề 15 phút (tăng sẵn instance + nạp đề vào Redis Cache).
  * Khi tải CPU hoặc kết nối DB vượt **80%**, cờ trạng thái trên Redis tự động kích hoạt **Exam Panic Mode** tạm ngắt các tác vụ phụ (AI Tutor toàn hệ thống, AI Pre-Grading, Analytics nặng) để ưu tiên 100% tài nguyên cho luồng thi.

### **C. Bài Tự luận, Hàng đợi Chấm chung, Blind Grading, Resubmit & Phúc khảo**

* **Nộp bài & Kiểm tra tệp (`[KẾ HOẠCH BỔ SUNG - MVP]` nâng cấp từ `[ĐÃ TRIỂN KHAI]` 20 MB):**
  * Chấp nhận gõ trực tiếp trên trình soạn thảo **Rich-text/Markdown** hoặc tải tệp **PDF/DOCX dung lượng ≤ 20 MB/tệp** (sửa lỗi UI cho phép tải file trực tiếp không cần nộp text trước; Backend xác thực dung lượng thật bằng S3 `headObject`).
  * **Kiểm tra lớp văn bản (Text-searchable):** Bắt buộc trích xuất được **≥ 50 từ (hoặc ≥ 300 ký tự chữ cái hợp lệ)**; từ chối PDF chỉ chứa ảnh scan trắng, vẫn chấp nhận PDF/DOCX văn bản có kèm biểu đồ/hình minh họa.
* **Hàng đợi chấm chung, Blind Grading đồng nhất, Rubric & Claim/Lock 30p:**
  * **Blind Grading đồng nhất (`[ĐÃ TRIỂN KHAI - CẦN VÁ LỖI]`):** Ẩn toàn bộ họ tên/email/mã học viên trong **tất cả** ngữ cảnh chấm bài: Hàng đợi chấm bài tự luận, Hộp thư chấm câu tự luận trong Quiz, và Màn hình chấm Phúc khảo *(vá lỗi lộ tên/email học viên tại `AppealService.java` và `PendingGradingPage.tsx`)*.
  * **Chấm theo Rubric từng tiêu chí & Claim/Lock có gia hạn (`[KẾ HOẠCH BỔ SUNG - MVP]`):** Khi GV bấm "Nhận chấm (Claim)", bài chuyển trạng thái `CLAIMED` và bị khóa **30 phút** kèm `lock_token`; khi GV đang gõ nhận xét, trình duyệt gửi heartbeat tự động gia hạn khóa; khi lưu điểm kiểm tra `lock_token` + `@Version` OCC (`[ĐÃ TRIỂN KHAI]`).
* **Nộp lại (`Resubmit` — theo thực tế mã nguồn `[ĐÃ TRIỂN KHAI]` kết hợp bảo vệ `CLAIMED`):**
  * Học viên được phép nộp lại bài trong giới hạn `max_submissions` và trước hạn nộp — bao gồm cả sau khi bài đã được chấm (`GRADED` → chuyển trạng thái **`PENDING_REGRADE`** để chấm lại theo phiên bản mới nhất).
  * **Ràng buộc an toàn:** Chặn nộp lại khi bài đang ở trạng thái **`CLAIMED`** (đang có GV giữ khóa chấm 30 phút) để tránh xung đột dữ liệu giữa lúc chấm.
* **Phúc khảo (`Regrade / Appeal` — mở rộng phạm vi theo code `[ĐÃ TRIỂN KHAI]` + nâng cấp `[KẾ HOẠCH BỔ SUNG - MVP]`):**
  * **Phạm vi:** Cho phép học viên gửi phúc khảo cho cả **Bài tập tự luận (`ASSIGNMENT_SUBMISSION`)** và **Lượt làm Quiz có câu tự luận hoặc nghi lỗi đề (`QUIZ_ATTEMPT`)**.
  * **Giới hạn:** Tối đa **01 lần/mục** trong vòng **07 ngày** kể từ thời điểm công bố điểm (`[ĐÃ TRIỂN KHAI]`).
  * **Định tuyến độc lập & Ngoại lệ Root Admin:** Hệ thống tự động định tuyến bài phúc khảo ẩn danh cho **Giảng viên thứ 2 độc lập trong cùng Môn học (`same Subject`)** (chặn người chấm ban đầu). Trường hợp bất khả kháng môn học chỉ còn duy nhất 01 GV khả dụng (chính người đã chấm), hệ thống chuyển ngoại lệ cho **Root Admin** xử lý kèm `AuditLog` mức cao.
  * **Điểm quyết định & Cảnh báo lệch > 15% (`[KẾ HOẠCH BỔ SUNG - MVP]`):** Điểm phúc khảo là điểm quyết định cuối cùng; hệ thống ghi log % độ lệch giữa 2 lần chấm và tự động gửi cảnh báo giám sát chất lượng cho Admin nếu độ lệch **> 15%**.
* **Tính năng mở rộng (`[STRETCH]`):** AI Pre-Grading gợi ý điểm theo Rubric & AI Viva Voce (2 câu vấn đáp xác thực tác giả).

### **D. Điều kiện Hoàn thành, Trọng số Cấu hình & Chứng nhận Số (Open Badges 3.0) `[QĐ]`**

* **Hiện trạng (`[ĐÃ TRIỂN KHAI]`):** Hệ thống đã hỗ trợ tự động hoàn thành khóa học khi đạt điểm tổng kết mặc định **≥ 70%** (`CourseCompletionService`), tự cấp mã chứng chỉ duy nhất `CERT-XXXXXXXX`, trang in chứng chỉ và đường dẫn xác thực công khai không cần đăng nhập (`GET /api/certificates/verify/{code}`).
* **Chuẩn hoàn thiện v1.4 (`[KẾ HOẠCH BỔ SUNG - MVP]`):** Học viên được cấp **Digital Certificate chuẩn [1EdTech Open Badges 3.0](https://www.imsglobal.org/spec/ob/v3p0/cert)** (tích hợp siêu dữ liệu **JSON-LD**, **chữ ký mật mã** và **mã QR + URL tra cứu công khai**) khi đạt đồng thời các điều kiện trong thời hạn học chủ động:
  1. Hoàn thành **100% các Module bắt buộc** (học tuần tự hoặc mở khóa nhanh qua Fast-track; các mục được Admin miễn trừ `Waiver` được loại khỏi điều kiện chặn).
  2. Điểm trung bình trọng số của các **Bài kiểm tra chính thức (`Post-test`, `Kỳ thi đồng loạt`, `Bài tập tự luận bắt buộc`)** theo tỷ trọng **`weight_%` (tổng = 100%)** do Admin cấu hình cho từng khóa đạt **≥ 70%** (ngưỡng qua môn Admin cấu hình được; **tuyệt đối không lấy điểm `Pre-test` thay thế cho `Post-test`**).
  3. Hoàn thành **Khảo sát đánh giá khóa học CSAT/NPS** trước khi hệ thống xuất chứng nhận số.

---

## **3.4. AI Tutor (RAG), Cached Mistake Explanation & Kiểm định Chất lượng AI `[QĐ]`**

1. **Access-Controlled Document RAG & Timestamp-Aware Socratic Tutor:**
   * **Nền tảng đã triển khai (`[ĐÃ TRIỂN KHAI]`):** Hỗ trợ 4 định dạng tài liệu chuẩn hóa (`TEXT`, `PDF` có lớp text ≤ 20 MiB, `Markdown .md` ≤ 5 MiB, `JSON .json` ≤ 5 MiB); kiểm soát phạm vi truy xuất chặt theo các **Lesson học viên đã được mở khóa** (Core LMS ký `lesson_scope_sha256`, `ai-tutor` xác thực chữ ký & lọc SQL `lesson_id = ANY(...)`); trích dẫn chính xác vị trí nguồn (`page_number` PDF, `heading` Markdown, `json_path` JSON); cơ chế tự từ chối (`ABSTAINED`, 0 trích dẫn) khi câu hỏi ngoài phạm vi học liệu; đồng bộ vòng đời nội dung qua Outbox → RabbitMQ + Synchronous Invalidation Handshake khi Admin sửa/xóa tài liệu.
   * **Nâng cấp Socratic, `.docx`/`.vtt` & Timestamp Video (`[KẾ HOẠCH BỔ SUNG - MVP]`):**
     * Bổ sung **Prompt phương pháp Socratic** (hướng dẫn học viên tự tư duy qua câu hỏi gợi mở, từ chối giải hộ bài tập).
     * Mở rộng parser hỗ trợ thêm **`.docx`** và **`.vtt`/`.srt`** (tùy chọn theo mục 3.1 #4 — bài học có `.vtt` sẽ trích xuất timestamped chunks; bài không có `.vtt` tiếp tục RAG trên `TEXT`/`PDF`/`MD`/`JSON`/`DOCX`).
     * **One-Click Timestamp Context:** Khi bài học có video kèm `.vtt` và học viên bấm *"Hỏi AI tại mốc này"*, hệ thống đính kèm `(lesson_id, current_video_timestamp)` cùng đoạn transcript `±90 giây` quanh mốc đó vào ngữ cảnh và trả về trích dẫn mốc `MM:SS` có nút nhảy thẳng tới video.
2. **Khóa AI Tutor chống gian lận ở tầng Backend:**
   * *Hiện trạng (`[ĐÃ TRIỂN KHAI]`):* `AiTutorAccessPolicy` chặn AI Tutor khi học viên có quiz `IN_PROGRESS` trong cùng khóa học.
   * *Chuẩn v1.4 (`[KẾ HOẠCH BỔ SUNG - MVP]`):* Nâng cấp sang **khóa xuyên toàn hệ thống theo `user_id`** đối với mọi API AI Tutor và AI Giải thích lỗi sai khi học viên đang có phiên làm bài `IN_PROGRESS` thuộc **`PRE_TEST`, `POST_TEST` hoặc `MASS_EXAM`** (chặn triệt để mở tab thứ 2 hoặc hỏi qua khóa khác). **Mở cho phép** hỏi gợi ý Socratic khi học viên đang làm **`MINI_QUIZ` tự luyện**.
3. **Giới hạn tần suất, Độ dài & Cửa sổ ngữ cảnh (FinOps Guardrail):**
   * **Quota ngày (`[KẾ HOẠCH BỔ SUNG - MVP]`):** Mặc định **20 lượt hỏi/học viên/ngày** (Admin cấu hình được), kết hợp rate-limit chống burst ở API Gateway (`[ĐÃ TRIỂN KHAI]`); chỉ mở cho học viên còn quyền học chủ động (khóa khi hết `access_expires_at` / `CoursePhase.ENDED`).
   * **Độ dài câu hỏi (`[KẾ HOẠCH BỔ SUNG - MVP]`):** Giới hạn tối đa **≤ 1.000 ký tự/câu hỏi** *(hạ từ 4.000 ký tự hiện tại xuống 1.000 ký tự đồng bộ trên FE/BE/AI để cân bằng giữa câu hỏi tiếng Việt có dán đoạn code và kiểm soát chi phí token)*.
   * **Cửa sổ ngữ cảnh hội thoại (`[ĐÃ TRIỂN KHAI]`):** Giữ **tối đa 03 lượt gần nhất (06 tin nhắn)** theo triển khai hiện tại của `ai-tutor` để kiểm soát chi phí input token và tránh loãng ngữ cảnh.
4. **AI Giải thích lỗi sai có bộ nhớ đệm (Cached Mistake Explanation — `[KẾ HOẠCH BỔ SUNG - MVP]`):**
   * Sau khi hoàn thành `MINI_QUIZ` (hoặc sau khi đã Đạt/hết lượt `POST_TEST`, hoặc sau khi ca thi `MASS_EXAM` đã đóng), học viên bấm *"Tại sao tôi chọn sai?"*.
   * Lưu Cache theo khóa kết hợp `(question_id, selected_option_id, prompt_version)`. Nguồn nạp Cache gồm: **Admin nhập/nạp sẵn lời giải thích** cho phương án nhiễu trong Ngân hàng câu hỏi **kết hợp cơ chế lazy-cache** (sinh 1 lần từ LLM ở lần đầu tiên có học viên hỏi, mọi học viên sau — kể cả ở chế độ Archived Read-Only — nhận kết quả tức thì `< 50ms` với **0 token mới**, mục tiêu Cache Hit Rate ≥ 85%–90%).
5. **Kiến trúc Provider-Agnostic, An toàn AI, Chịu lỗi & Đánh giá P5:**
   * **Model & Adapter (`[ĐÃ TRIỂN KHAI]`):** Kiến trúc Provider-Agnostic hỗ trợ chuyển đổi linh hoạt qua cấu hình môi trường: Generation (`Groq`, `Gemini 2.5 Flash`, `GPT-4o-mini`), Embedding (`Jina`, `OpenAI`, `Gemini`), Vector Store (`pgvector` mặc định, `Pinecone` tùy chọn). Môi trường Dev/Demo hiện dùng **Groq (`openai/gpt-oss-120b`) + Jina (1024d)**; Production khuyến nghị **Gemini 2.5 Flash / GPT-4o-mini**.
   * **An toàn & Xử lý lỗi (`[KẾ HOẠCH BỔ SUNG - MVP]`):** Không gửi PII sang LLM (`[ĐÃ TRIỂN KHAI]` không truyền tên/email/IP; bổ sung `ephemeral_session_hash`); tách biệt dữ liệu đầu vào/trích đoạn bằng **XML Delimiters** chống Prompt Injection; tách rõ mục tiêu **TTFT < 3s (khi có SSE streaming)** và **Hard Timeout gọi LLM 15–20 giây** + **Circuit Breaker**; khi LLM lỗi (`Timeout / 429 / 503`), kích hoạt **Retrieval-only Fallback** trả về ngay **Top-3 đoạn học liệu/transcript liên quan nhất** từ `pgvector` kèm thông báo thân thiện thay vì lỗi 503 trắng.
   * **Evaluation Pipeline (P5) & Token FinOps (`[KẾ HOẠCH BỔ SUNG - MVP]`):** Xây dựng bộ kiểm thử tự động đánh giá RAG (Recall@K, Groundedness, Out-of-scope Refusal Rate, Indirect Prompt Injection, độ trễ & chi phí token/request); lưu bền vững `input_tokens` và `output_tokens` vào CSDL và hiển thị thống kê chi phí trên Dashboard Admin.
6. **Các tính năng AI Mở rộng (`[STRETCH]`):**
   * **AI Quiz Generator (Admin-in-the-loop):** AI sinh câu hỏi trắc nghiệm nháp theo LO/Module & mức Bloom kèm lời giải thích phương án nhiễu → chỉ Admin độc quyền kiểm duyệt (`Approve / Edit / Reject`) trước khi đưa vào Ngân hàng câu hỏi *(chuyển từ MVP sang `[STRETCH]`; Ngân hàng câu hỏi thủ công + Import/Export CSV/JSON vẫn là `[ĐÃ TRIỂN KHAI]` trong MVP)*.
   * **AI Viva Voce** (vấn đáp 2 câu xác thực tác giả bài tự luận) & **AI Pre-Grading** (gợi ý chấm theo Rubric).
   * **Semantic FAQ Deflection & Verified Q&A → RAG Auto-Ingestion.**
   * **FSRS Spaced Repetition & Adaptive Micro-Remediation Playlist.**

## **3.5. Tính năng Vận hành Trung tâm Bổ trợ (Center Operations — `[ĐÃ TRIỂN KHAI]` `[QĐ]`)**

Nhằm phục vụ vận hành thực tế của một trung tâm đào tạo Single-Tenant (đồng bộ với [BUSINESS.md](../backend/BUSINESS.md)), hệ thống bao gồm **05 tính năng vận hành bổ trợ đã triển khai hoàn chỉnh**:

1. **Sổ cái Học phí Thủ công, Công nợ, Hoàn tiền, Miễn giảm & In biên lai (`[ĐÃ TRIỂN KHAI]`):**
   * **Out-of-Scope cổng thanh toán trực tuyến:** Hệ thống **không** tích hợp cổng thanh toán online và **không** lưu trữ dữ liệu thẻ/ngân hàng.
   * Khóa học có thể đặt `feeAmount` tùy chọn; Admin ghi nhận thủ công các giao dịch thu tại trung tâm (`PAYMENT` / `DISCOUNT` / `REFUND`) vào sổ cái bất biến `tuition_payments` (không sửa/xóa dòng cũ), hệ thống tự tính `UNPAID` / `PARTIAL` / `PAID`, quản lý công nợ và xuất phiếu/biên lai in được (`BL/MG/HT-yyyyMMdd-xxxxxx`).
2. **Lịch học theo Buổi & Điểm danh (`[ĐÃ TRIỂN KHAI]`):**
   * Tính năng **tùy chọn theo từng khóa học** dành cho các khóa có buổi học trực tuyến cố định/hỗ trợ giải đáp (Blended / Live Q&A sessions): quản lý `course_sessions`, điểm danh (`PRESENT` / `LATE` / `EXCUSED` / `ABSENT`), thông báo `SESSION_SCHEDULED` và lịch tổng hợp cá nhân (`/student/calendar`).
3. **Danh sách chờ khi đầy sĩ số (`WAITLISTED` — `[ĐÃ TRIỂN KHAI]`):**
   * Khi khóa học đạt tối đa sĩ số (`capacity`), học viên nhập mã hợp lệ được đưa vào trạng thái `WAITLISTED` và tự động thăng lên `ACTIVE` theo thứ tự **FIFO** ngay khi có chỗ trống (hoặc Admin thăng/loại thủ công).
4. **Điều kiện tiên quyết giữa Khóa học (`CoursePrerequisite` — `[ĐÃ TRIỂN KHAI]`):**
   * Cho phép yêu cầu học viên hoàn thành (`COMPLETED`) khóa nền tảng trước khi ghi danh khóa nâng cao; thuật toán **BFS** chặn vòng lặp điều kiện tiên quyết ngay lúc cấu hình.
5. **Ghi danh Hàng loạt (`Bulk Enroll`) & Miễn trừ Mục Đánh giá (`Waiver`) (`[ĐÃ TRIỂN KHAI]`):**
   * Admin chọn mã mời của khóa và dán danh sách email để ghi danh hàng loạt (tự tạo tài khoản kèm mật khẩu tạm nếu chưa có; mỗi dòng chạy transaction `REQUIRES_NEW` độc lập không rollback cả lô) và xuất roster/học phí ra CSV UTF-8 BOM.
   * Admin có quyền cấp miễn trừ (`Waiver`, bắt buộc ghi lý do + `AuditLog`) cho từng bài quiz/bài tập cụ thể của một học viên để mục quá hạn/bất khả kháng không chặn xét tốt nghiệp.

---

# **4. TIÊU CHUẨN CHẤT LƯỢNG, PHÂN TÍCH HỌC TẬP & TUÂN THỦ `[QĐ]`**

## **4.1. Sư phạm & Phân tích Học tập (Learning Analytics)**

1. **Constructive Alignment:** Hỗ trợ ánh xạ học liệu, quiz, bài tự luận tới **LO (tùy chọn theo khóa)** hoặc theo cấp Module/Lesson.
2. **Learning Analytics:**
   * **Đã triển khai (`[ĐÃ TRIỂN KHAI]`):** Dashboard thống kê Admin/GV (tỷ lệ hoàn thành/bỏ học theo khóa, xu hướng ghi danh 6 tháng, danh sách lớp & tiến độ bài học, Bảng điểm khóa học xuất CSV, phân tích tỷ lệ đúng theo từng câu hỏi quiz).
   * **Kế hoạch bổ sung MVP (`[KẾ HOẠCH BỔ SUNG - MVP]`):** **Drop-off Rate theo từng bài học**, **Learning Gain (`Điểm Post-test − Điểm Pre-test`)**, **Bản đồ thành thạo LO** cho học viên (khi khóa có gắn LO), thống kê **CSAT/NPS** và **Dashboard FinOps chi phí Token AI**.
   * **Mở rộng (`[STRETCH]`):** **Rewind Heatmap & Misconception Radar** (thống kê phân đoạn video 30s bị tua lại nhiều nhất và phương án nhiễu bị chọn sai nhiều nhất) cùng **Adaptive Remediation Playlist**.

## **4.2. Chất lượng Kỹ thuật (ISO/IEC 25010 & NFRs)**

| Đặc tính | Chỉ số mục tiêu (NFR Targets) | Hiện trạng & Giải pháp kỹ thuật v1.4 |
| :--- | :--- | :--- |
| **Performance Efficiency** | • Chịu tải **≥ 500 CCU** trong ca thi đồng loạt (duy trì ~30–60 RPS; đỉnh mở đề/nộp bài ~150–200 RPS).<br>• Độ trễ API nghiệp vụ: **p95 < 300 ms** *(siết `alerts.yml` từ 500ms về 300ms `[KẾ HOẠCH BỔ SUNG - MVP]`)*.<br>• Khởi tạo phát Video HLS: **< 2,0 s**.<br>• AI Tutor TTFT: **< 3,0 s** (khi bật SSE); Hard Timeout gọi LLM **15–20 s**; Cache lỗi sai **< 50 ms**. | • `[ĐÃ TRIỂN KHAI]` Baseline k6: 200 HV duyệt khóa đạt `p95 = 23 ms` (0% lỗi); 300 HV thi đạt `0%` lỗi (`POST submit p95 = 24–26 ms` với 2 bản `core-lms`); PostgreSQL Primary + Read Replica; Redis cache-aside; Outbox + RabbitMQ.<br>• `[KẾ HOẠCH BỔ SUNG - MVP]` Chuyển luồng thi sang **Redis Cache (CQRS Read Model) + Delta Sync + `exam-submission-queue`**, chạy lại load test mốc **500 CCU**; nâng cấp CDN Edge cho video HLS (`[TBD]` Phương án A hoặc B). |
| **Scalability & Reliability** | • Availability: **≥ 99,5%** toàn thời gian (**≥ 99,9%** trong khung giờ ca thi).<br>• **RPO ≤ 15 phút** dữ liệu hệ thống; **RPO ≤ 20 giây** cho phiên thi (**≤ 5 giây** ở 60s cuối và **RPO ≈ 0** trên trình duyệt nhờ IndexedDB).<br>• **RTO ≤ 30 phút**. | • `[ĐÃ TRIỂN KHAI]` Stateless containers, ECS Fargate CPU Target Tracking 60% (1–4 tasks), DNS round-robin ở Gateway, Chaos test 6 kịch bản (Redis/RabbitMQ/Postgres/Service down không sập dây chuyền), Outbox + DLQ.<br>• `[KẾ HOẠCH BỔ SUNG - MVP]` **Scheduled Pre-warming** (`aws_appautoscaling_scheduled_action` trước ca thi 15p), IndexedDB + Redis Auto-Finalize, **Exam Panic Mode (Redis Circuit Breaker)**, triển khai Prometheus/Grafana/Alarms trên AWS. |
| **Security** | • TLS 1.3 trên đường truyền (CloudFront HTTPS); AES-256 ở tầng lưu trữ (S3 SSE-AES256); 100% API qua RBAC.<br>• Bảo vệ HLS tại Edge (`[TBD]` AWS CloudFront OAC + Signed Cookies hoặc Cloudflare R2 + Worker HMAC) + **Dynamic Watermark**.<br>• Chống gian lận & AI Safety: khóa AI xuyên khóa theo `user_id`, XML Delimiters, `ephemeral_session_hash`. | • `[ĐÃ TRIỂN KHAI]` JWT RS256 + JWKS xoay khóa, Redis token revocation, khóa đăng nhập sai, chính sách mật khẩu mạnh, chặn file thực thi, Secrets Manager / env guard, S3 private + IAM role không access key, `AuditLog` toàn diện.<br>• `[KẾ HOẠCH BỔ SUNG - MVP]` Email verification, Root Admin guard, 3 thiết bị / 1 Active Stream / Single-Device Exam Lock, Edge Signed Cookies + Watermark, kiểm `headObject` ở `assessment-service`. |
| **Interoperability** | • 100% RESTful API chuẩn **OpenAPI 3.0** (`[ĐÃ TRIỂN KHAI]` cả Java & Python `ai-tutor`).<br>• Import/Export Ngân hàng câu hỏi & Bảng điểm chuẩn **CSV / JSON** (`[ĐÃ TRIỂN KHAI]`).<br>• Chứng nhận số chuẩn **1EdTech Open Badges 3.0** (`[KẾ HOẠCH BỔ SUNG - MVP]`). | • Swagger/OpenAPI tự động sinh tài liệu kiểm thử; chứng nhận số JSON-LD có chữ ký mật mã + QR. |
| **Usability & Accessibility** | • Responsive Web (Desktop/Tablet/Mobile), **Tiếng Việt mặc định** (`[ĐÃ TRIỂN KHAI]`).<br>• **Song ngữ Tiếng Anh (i18n):** **`[STRETCH]`**.<br>• **W3C WCAG 2.1 Level AA cơ bản:** nhãn ARIA (`[ĐÃ TRIỂN KHAI]`), phụ đề `.vtt` tùy chọn & điều khiển 100% bằng bàn phím ở trình phát video và trình làm quiz (`[KẾ HOẠCH BỔ SUNG - MVP]`). | • Trình phát `hls.js` + `<track>` phụ đề `.vtt`; bổ sung `onKeyDown` phím mũi tên/số và focus trap trên `QuizTakingModal` & `Modal`. |

## **4.3. Tuân thủ Pháp lý & Bảo vệ Dữ liệu Cá nhân `[QĐ]`**

1. **Khung pháp lý áp dụng:** Tuân thủ **Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15** và **Nghị định 356/2025/NĐ-CP** (hiệu lực từ 01/01/2026).
2. **Đồng ý rõ ràng (Explicit Consent — `[ĐÃ TRIỂN KHAI]`):** Học viên xác nhận đồng ý rõ ràng ngay lúc đăng ký tài khoản (`users.consent_at`) về mục đích thu thập dữ liệu tiến độ, nhật ký thi và dữ liệu hành vi video (Heartbeat).
3. **Ẩn danh hóa trước khi gọi LLM (PII Stripping):** Không gửi Họ tên, Email, SĐT, IP sang API LLM (`[ĐÃ TRIỂN KHAI]`); bổ sung mã phiên ẩn danh `ephemeral_session_hash` (`[KẾ HOẠCH BỔ SUNG - MVP]`).
4. **Quyền chủ thể dữ liệu & Tài khoản Ngủ đông 24 tháng:**
   * Học viên tự xuất dữ liệu cá nhân dạng JSON (`GET /api/users/me/export`) và Admin ẩn danh hóa PII theo yêu cầu (`POST /api/admin/users/{id}/anonymize`) (`[ĐÃ TRIỂN KHAI]`).
   * Bổ sung luồng **học viên tự gửi yêu cầu khóa/xóa tài khoản** và **Worker Dormant 24 tháng** tự động xóa/ẩn danh hóa Heartbeat logs và chuyển tài khoản sang `DORMANT` (mở lại qua Email OTP) (`[KẾ HOẠCH BỔ SUNG - MVP]`).

---

# **5. ĐÁNH ĐỔI TRONG THIẾT KẾ MVP (ARCHITECTURE & PRODUCT TRADE-OFFS) `[QĐ]`**

| Bài toán thiết kế | Không làm trong MVP (Out-of-Scope / Stretch) | Giải pháp lựa chọn trong v1.4 & Lý do bảo vệ (Rationale) |
| :--- | :--- | :--- |
| **1. Kiến trúc Ứng dụng** | Microservices phân tán phức tạp (chục service, service mesh, Kubernetes) | Chọn **Modular Service-Oriented Architecture (4 Java services + 1 Python `ai-tutor`)** (`[ĐÃ TRIỂN KHAI]`): tách riêng **`assessment-service`** (chịu tải đỉnh khi thi) khỏi **`core-lms-service`**, tách **`worker-service`** cho tác vụ bất đồng bộ (FFmpeg HLS, Email, Outbox, Exam Finalizer) và tách **`ai-tutor` (Python/FastAPI)** để tận dụng tối đa hệ sinh thái AI/PDF/RAG Python. Đơn giản hơn Kubernetes (chạy Docker Compose / ECS Fargate) nhưng đã chứng minh cô lập lỗi qua Chaos Test. |
| **2. Sở hữu Dữ liệu & Vector DB** | Cụm Vector DB trả phí độc lập (Milvus/Qdrant) làm tăng chi phí vận hành | Dùng **PostgreSQL 16 (Primary + Read Replica)** cho dữ liệu nghiệp vụ LMS và tách riêng **PostgreSQL 17 + `pgvector` (`ai-postgres`)** theo `ADR-0015` (`[ĐÃ TRIỂN KHAI]`): cô lập hoàn toàn tải truy vấn vector khỏi DB giao dịch chính, đồng thời vẫn đảm bảo bảo mật phân quyền bài đã mở khóa bằng **signed assertion (`lesson_scope_sha256`) + exact SQL filtering** (không phụ thuộc JOIN chéo DB). |
| **3. Media & Chi phí Băng thông (Egress)** | Đẩy luồng byte video đi xuyên API Server; chuẩn SCORM/LTI phức tạp | Upload trực tiếp qua Presigned URL, đóng gói HLS **360p/720p** bất đồng bộ (`[ĐÃ TRIỂN KHAI]`). Về Edge CDN & bảo vệ HLS, đối chiếu **2 phương án đang nghiên cứu (`[TBD]`):** *Phương án A (Thuần AWS S3 + CloudFront OAC Signed Cookies — đồng nhất hạ tầng AWS)* vs. *Phương án B (Hybrid AWS Compute + Cloudflare R2 \$0 Egress + Worker HMAC — tối ưu chi phí băng thông khi xem lại tĩnh vĩnh viễn)*. |
| **4. Chống gian lận thi cử** | Webcam AI Proctoring, Safe Exam Browser xâm phạm riêng tư | Kết hợp **Đồng hồ máy chủ + Hard Cutoff + Đảo đề/đáp án + Khóa AI Tutor xuyên khóa ở Backend + Single-Device Exam Lock + Dynamic Watermark** (`+ [STRETCH] AI Viva Voce`). |
| **5. Phân quyền biên soạn & Chấm bài** | Instructor tạo/sửa khóa học hoặc Admin thường chấm bài | **Chỉ Admin** quản lý giáo trình/LO/Rubric/ngân hàng đề; **Instructor** tập trung chấm bài (Blind Grading + Rubric + Claim 30p), Q&A, `Announcement` và `Flag Issue`. **Admin thường tuyệt đối không chấm**; chỉ **Root Admin** có nhánh fallback ngoại lệ cuối cùng kèm `AuditLog` khi môn chỉ còn 01 GV duy nhất. |
| **6. Cổng thanh toán trực tuyến** | Tích hợp VNPay/Stripe, lưu dữ liệu thẻ/ngân hàng | **Out-of-Scope cổng thanh toán online**; trung tâm thu học phí ngoài hệ thống và ghi nhận qua **Sổ cái học phí thủ công (`tuition_payments`)** (`[ĐÃ TRIỂN KHAI]`) kết hợp phát hành Mã mời / Mã kích hoạt. |

---

# **6. YÊU CẦU CHỨC NĂNG CỐT LÕI (FUNCTIONAL REQUIREMENTS — FR-01 – FR-09)**

| Mã FR | Nhóm chức năng | Đặc tả chi tiết v1.4 & Trạng thái |
| :--- | :--- | :--- |
| **FR-01** | Quản lý Khóa học, LO Tùy chọn & Media | Chỉ Admin tạo/sửa `Subject → Course → Module → Lesson`, gắn **LO & Bloom (tùy chọn)**, Ngân hàng câu hỏi (thủ công + CSV/JSON `[ĐÃ TRIỂN KHAI]`, AI Quiz Gen `[STRETCH]`), Rubric, tỷ trọng điểm `weight_% = 100%`, **Publish Validation 2 lớp** (kèm ≥ 2 GV cho khóa có tự luận). Upload Presigned URL (`.mp4 ≤ 2GB`, `.vtt/.srt ≤ 5MB` tùy chọn, `PDF/DOCX ≤ 20MB`), kiểm size thật S3, Async FFmpeg HLS **360p/720p** (`[ĐÃ TRIỂN KHAI]`) + `.vtt` parser (`[KẾ HOẠCH BỔ SUNG - MVP]`). |
| **FR-02** | Xác thực, Root Admin, Ghi danh & Thời hạn học | Self-registration (`PENDING_VERIFICATION` + OTP/Link Email 15–30p + Explicit Consent); Admin tạo được cả 3 vai trò (chỉ **Root Admin** tạo/khóa `ADMIN`). Hỗ trợ **2 chế độ mã** (mã mời lớp `max_uses > 1` `[ĐÃ TRIỂN KHAI]` & mã kích hoạt 1 lần `max_uses = 1`, lô CSV/QR, cấp lại, Mã gia hạn `[KẾ HOẠCH BỔ SUNG - MVP]`). Thời hạn học kết hợp **đợt khóa (`startAt/endAt`)** và/hoặc **cá nhân (`access_period_days` → `access_expires_at`)**; hết hạn chuyển **Archived Read-Only Mode** + 2 rào chắn (1 phiên/1 luồng + 15h/tháng; Dormant 24 tháng). |
| **FR-03** | Học tập, Kiểm soát Luồng phát & Lộ trình Thích ứng | Phát HLS 360p/720p (`[ĐÃ TRIỂN KHAI]`) + phụ đề `.vtt` tùy chọn + **Edge Signed Cookies (`[TBD]` A/B) + Dynamic Watermark** (`[KẾ HOẠCH BỔ SUNG - MVP]`). Tối đa **03 thiết bị**, khóa cứng **01 luồng video hoạt động (Redis Heartbeat 30s)**, **Single-Device Exam Lock**. Chế độ **`SEQUENTIAL` mặc định** (tùy chọn `FREE_NAVIGATION`), toán tử **AND** (≥ 80% video đo từ Heartbeat 30s + anti-seek + xác nhận đọc tài liệu VÀ ≥ 70% Mini-quiz); **Fast-track Pre-test ≥ 85%** theo LO (hoặc Module nếu không dùng LO). |
| **FR-04** | Khảo thí 4 loại Quiz, Học bù & Kỳ thi Đồng loạt 500 CCU | 4 loại `PRE_TEST` (`max=1`), `MASS_EXAM` (`max=1`), `POST_TEST` (ẩn đáp án/AI động + **Remediation** xem lại 100% video Module & Cooldown 24h), `MINI_QUIZ` (`Unlimited`, gợi ý Socratic `< 70%`, trộn Pool theo LO/Module). Mass Exam: `T_start/T_end`, vào muộn ≤ 15p, Hard Cutoff `T_end`, **IndexedDB + Redis Delta Sync (20s / 5s ở 60s cuối)**, Client Jitter (0–10s), Grace Window 30s, Server-side Auto-Finalize, Queue `W01` chấm bất đồng bộ, Cảnh báo > 500 CCU, Pre-warming 15p, **Exam Panic Mode**. |
| **FR-05** | Tự luận, Hàng đợi Chấm, Rubric, Resubmit & Phúc khảo | Rich-text/Markdown + PDF/DOCX ≤ 20 MB (`headObject` S3) trích xuất ≥ 50 từ / 300 ký tự; **Blind Grading đồng nhất** (bài tập, câu tự luận quiz, phúc khảo); **Rubric từng tiêu chí**; **Claim/Lock 30p (Auto-extend Lease + `lock_token` + `@Version`)**. Cho **Resubmit** trong `max_submissions` & trước hạn (kể cả sau `GRADED` → `PENDING_REGRADE`), chặn khi `CLAIMED`. **Phúc khảo** cả tự luận & quiz (1 lần/7 ngày), định tuyến GV thứ 2 cùng môn (fallback **Root Admin** + AuditLog), cảnh báo Admin khi lệch > 15%. (`[STRETCH]`: AI Pre-Grading, AI Viva Voce.) |
| **FR-06** | Q&A, Thông báo Khóa học, Flag Issue & Email DLQ | Q&A theo khóa & bài học (`lesson_id`, đính kèm file `[ĐÃ TRIỂN KHAI]`, gắn mốc giây video `[KẾ HOẠCH BỔ SUNG - MVP]`), GV đăng `Announcement` (`[ĐÃ TRIỂN KHAI]`), GV bấm **Flag Content/Rubric Issue** (`[KẾ HOẠCH BỔ SUNG - MVP]`). Thông báo In-app & Email bất đồng bộ qua Outbox → RabbitMQ + Retry 3 lần + DLQ (`[KẾ HOẠCH BỔ SUNG - MVP]` sửa `EmailSender` không nuốt lỗi & bật SMTP theo env). |
| **FR-07** | Chứng nhận Mở (Open Badges 3.0), Analytics & Tuân thủ | Tự động hoàn thành (100% Module + điểm trọng số bài thi chính thức `weight_% = 100%` ≥ 70%, loại Pre-test) + **CSAT/NPS** → xuất **Open Badges 3.0** (JSON-LD + chữ ký mật mã + QR/URL công khai). Dashboard: Completion, **Drop-off theo từng bài**, **Learning Gain**, **Bản đồ LO**, **FinOps AI**. Tuân thủ Luật 91/2025 & NĐ 356/2025: Export JSON (`[ĐÃ TRIỂN KHAI]`), tự yêu cầu khóa/xóa tài khoản & Dormant 24 tháng (`[KẾ HOẠCH BỔ SUNG - MVP]`). (`[STRETCH]`: Tiếng Anh i18n, Rewind Heatmap.) |
| **FR-08** | Trợ lý AI Socratic, Mistake Cache & Evaluation P5 | Document RAG 4 định dạng (`TEXT/PDF/MD/JSON`) + Access-Controlled RAG + Abstention (`[ĐÃ TRIỂN KHAI]`). Bổ sung: **Prompt Socratic**, parser `.docx`/`.vtt` tùy chọn + **One-Click Timestamp (`±90s`)**, khóa AI **xuyên khóa theo `user_id`** (mở Mini-quiz), quota **20 lượt/ngày**, câu hỏi **≤ 1.000 ký tự**, ngữ cảnh **3 lượt (6 tin nhắn)**, **Cached Mistake Explanation** (nạp sẵn + lazy-cache, mở cho Archived), XML Delimiters, TTFT < 3s / Hard Timeout 15–20s + Circuit Breaker + **Retrieval-only Fallback (Top-3)**, **Eval Pipeline P5** & lưu Token FinOps (`[KẾ HOẠCH BỔ SUNG - MVP]`). (`[STRETCH]`: AI Quiz Generator, FSRS, Semantic FAQ.) |
| **FR-09** | Vận hành Trung tâm Bổ trợ (`Center Operations`) | 05 tính năng đã triển khai (`[ĐÃ TRIỂN KHAI]`): Sổ cái học phí thu tay/công nợ/hoàn tiền/miễn giảm/biên lai (không cổng thanh toán online), Lịch học & Điểm danh (tùy chọn theo khóa), Danh sách chờ (`WAITLISTED`) FIFO, Điều kiện tiên quyết khóa học (BFS), Ghi danh hàng loạt email (`Bulk Enroll`) & Miễn trừ mục đánh giá (`Waiver`). |

---

# **7. PHÂN TÍCH TẢI & ƯỚC TÍNH CHI PHÍ (WORKLOAD & FINOPS ANALYSIS) `[QĐ]`**

## **7.1. Ước tính Tải Hệ thống (1.500 Học viên Hoạt động · Đỉnh Thi 500 CCU)**

| Hạng mục tải | Đặc tính Workload | Giả định định lượng | Mục tiêu thiết kế |
| :--- | :--- | :--- | :--- |
| **1. Lưu trữ Học liệu (Object Storage)** | Actual File → Object Storage | 5 khóa × 40 Lesson = 200 Lesson; mỗi Lesson 1 video 60p ở 2 mức HLS (**360p + 720p** ≈ 0,85–1,0 GB) + tài liệu/phụ đề `.vtt` (20 MB) + bài nộp PDF/DOCX (~20 GB). | **~220 – 250 GB** |
| **2. Băng thông Video (Media Egress)** | Read-Heavy Media Stream | 1.500 HV active × 8h/tháng × 0,6 GB/h ≈ 7.200 GB + ~500 HV xem lại tĩnh (giới hạn 15h/tháng, thực tế ~2h/tháng ≈ 600 GB) → **~7,8 – 8,5 TB/tháng**; giờ cao điểm ~300 HV đồng thời ≈ 400–450 Mbps (Edge CDN gánh 95%+). | **~8,0 – 8,5 TB Egress/tháng** qua Edge CDN |
| **3. Tiến độ & Active Stream Lock** | Buffered Write | 300 HV xem video đồng thời, Heartbeat mỗi 30s → 10 RPS ghi Redis (giữ khóa 1 luồng + đếm thời lượng xem) rồi gom lô flush xuống PostgreSQL. | **10 – 15 RPS** |
| **4. Tải Duy trì Ca thi Đồng loạt** | Sustained Write | **500 CCU** làm bài; Dirty-state Delta Sync mỗi 20s (~15–25 RPS); 60s cuối Aggressive Sync 5s/lần cho ~150 HV còn đổi đáp án → 30 RPS vào Redis. | **30 – 60 RPS** |
| **5. Tải Đỉnh Mở đề & Nộp bài** | Read Burst (`T_start`) & Write Burst (`T_end`) | Mở đề: 500 HV đọc 100% từ **Redis Cache** (50–100 RPS). Hết giờ: ~350 HV rải qua **Client Jitter (0–10s)** → ~35 RPS đẩy vào `exam-submission-queue` + polling trạng thái ≈ 120–180 RPS tại API Gateway. | Chịu đỉnh **200 RPS** tại Gateway / Queue |
| **6. Lưu lượng AI & RAG** | Vector Search + Mistake Cache | ~450 HV/ngày × 5 câu/ngày × 30 ngày = 67.500 lượt/tháng (ngữ cảnh 3 lượt, câu hỏi ≤ 1.000 ký tự → ~1.200 input + ~350 output tokens/lượt). Mistake Cache đạt Hit ≥ 90% (< 3.000 lượt LLM mới/tháng; HV xem tĩnh tốn 0 token). | **~85 – 100M Input** & **~25M Output Tokens / tháng** |

## **7.2. Ước tính Chi phí Cloud & AI Hàng tháng — So sánh 02 Phương án Media (`[TBD]` nối tiếp mục 3.1 #4)**

Hệ thống Compute/DB/Cache/MQ đã có sẵn mã nguồn Terraform AWS (`infra/aws/ec2-demo` và `infra/aws/ecs-managed`). Bảng dưới đối chiếu chi phí thực tế giữa **Môi trường Demo/Đồ án** và **Môi trường Production (1.500 HV active + ~8 TB video/tháng)** theo 2 phương án Media đang nghiên cứu:

| Thành phần hạ tầng | Demo / Load Test Đồ án (`infra/aws/ec2-demo` `[ĐÃ TRIỂN KHAI]`) | Production — **Phương án A: Thuần AWS** (`ecs-managed` + CloudFront OAC) | Production — **Phương án B: Hybrid AWS Compute + Cloudflare R2 Media** |
| :--- | :--- | :--- | :--- |
| **1. Compute & CI/CD** | 1 × EC2 `c7i-flex.large` (4 GB + 2 GB swap, Docker Compose + ECR/GitHub Actions): **~\$2–3/ngày** *(chạy bằng AWS Free Plan credit, `terraform destroy` sau demo)* | AWS ECS Fargate (6 services: 5 service hiện tại `gateway`, `core-lms`, `assessment`, `worker`, `frontend` + bổ sung `ai-tutor`, autoscaling 1–4 tasks) + ALB + NAT GW: **~\$80 – \$110 / tháng** | Giống Phương án A (AWS ECS Fargate 6 services + ALB + NAT GW): **~\$80 – \$110 / tháng** |
| **2. Database, Cache & Queue** | Chạy container chung trên EC2 (`Postgres 16`, `ai-postgres` pgvector, `Redis 7`, `RabbitMQ 3.13`): **\$0 phát sinh** | RDS PostgreSQL 16 (`db.t4g.small`) + `ai-postgres` pgvector + ElastiCache Redis 7.1 (`cache.t4g.micro`) + Amazon MQ RabbitMQ (`mq.t3.micro`): **~\$65 – \$95 / tháng** | Giống Phương án A: **~\$65 – \$95 / tháng** |
| **3. Object Storage & Media CDN (8 TB Egress/tháng)** | AWS S3 (< 10 GB) + CloudFront Free Tier (1 TB/tháng): **\$0 – \$2 / tháng** | AWS S3 (250 GB: ~\$6) + **CloudFront Egress 8 TB** (trừ 1 TB Free Tier → ~7 TB × \$0.085/GB): **~\$600 / tháng** *(đồng nhất 100% AWS, nhưng phí egress cao khi mở xem lại tĩnh vĩnh viễn)* | **Cloudflare R2** (250 GB: ~\$4, **\$0 phí Egress**) + Cloudflare Workers Paid/WAF (\$5): **~\$9 – \$12 / tháng** *(tiết kiệm ~\$590/tháng ở quy mô 8 TB/tháng)* |
| **4. Dịch vụ AI (LLM + Embedding)** | Groq / Jina / Gemini Free-Low Tier cho Demo & Eval P5: **\$0 – \$5 / tháng** | ~90M Input + 25M Output tokens (Gemini 2.5 Flash / GPT-4o-mini + Mistake Cache ≥ 90%): **~\$20 – \$30 / tháng** | Giống Phương án A: **~\$20 – \$30 / tháng** |
| **5. Observability, Secrets & Email** | AWS Secrets/SSM + Email Sandbox: **\$0 – \$3 / tháng** | CloudWatch / Grafana Cloud + Secrets Manager + Amazon SES: **~\$10 – \$15 / tháng** | Giống Phương án A: **~\$10 – \$15 / tháng** |
| **TỔNG CHI PHÍ DỰ KIẾN** | **~\$2 – \$3 / ngày demo** *(tối ưu bằng Free Plan credit)* | **~\$775 – \$850 / tháng** *(~0,52–0,57 USD/HV/tháng; thích hợp giai đoạn đầu < 1 TB/tháng ~\$180–250/tháng)* | **~\$184 – \$262 / tháng** *(~0,12–0,17 USD/HV/tháng; tối ưu nhất khi băng thông video > 1 TB/tháng)* |

> **Ghi chú quyết định `[TBD]`:** Nhóm sẽ nghiên cứu chi tiết Phương án B (Hybrid AWS Compute + Cloudflare R2) trước khi chốt chính thức cơ chế Edge Auth HLS cho giai đoạn Production; trong giai đoạn Demo đồ án (< 1 TB/tháng), Phương án A (Thuần AWS) đang miễn phí băng thông trong hạn mức CloudFront Free Tier.

---

# **8. KIẾN TRÚC HỆ THỐNG & KỊCH BẢN CHỊU LỖI (APPLICATION, DATA & FAILURE HANDLING) `[QĐ]`**

## **8.1. Sơ đồ Kiến trúc Tổng thể (Phản ánh trung thực hệ thống triển khai v1.4)**

```text
[ Học viên / Giảng viên / Admin (React SPA + hls.js + Dynamic Watermark + IndexedDB Exam Store) ]
          │                                              │
          │ (1) Video HLS 360p/720p (.m3u8/.ts)          │ (2) REST API / SSE (HTTPS via CloudFront)
          │     & Direct Upload (Presigned URL)          │
          ▼                                              ▼
┌─────────────────────────────────────────┐    ┌──────────────────────────────────────────────────┐
│ Media Edge CDN & Auth ([TBD]: A hoặc B) │    │  AWS CloudFront (HTTPS) → Nginx / ALB            │
│ • PA-A: AWS CloudFront OAC + Signed     │    │  → api-gateway :8080 (Spring Cloud Gateway)      │
│   Cookies (RSA Key Group)               │    │    • JWT RS256/JWKS + Redis Token Revocation     │
│ • PA-B: Cloudflare CDN + Worker HMAC    │    │    • Per-Route Rate Limit + Idempotency-Key      │
└───────────────────┬─────────────────────┘    │    • Resilience4j CB + DNS Round-Robin           │
                    │                          └────────┬───────────────────────┬─────────────────┘
                    ▼                                   │                       │
┌─────────────────────────────────────────┐             ▼                       ▼
│ Object Storage (S3 / MinIO [hoặc R2])   │   ┌──────────────────────┐ ┌──────────────────────────┐
│ • Raw MP4 (≤2GB), .vtt (≤5MB), Docs     │◄──┤ core-lms-service:8081│ │ assessment-service :8082 │
│ • HLS 360p/720p Segments & Submissions  │   │ • Auth/RBAC/RootAdmin│ │ • 4 Quiz Types & Bank    │
└───────────────────┬─────────────────────┘   │ • Course/LO/Media    │ │ • Mass Exam (Redis+Queue)│
                    │                         │ • Enroll/Access/Arch │ │ • Blind Grading + Rubric │
                    │                         │ • Sequential/Progress│ │ • Claim/Lease 30p/Appeal │
                    │                         │ • Q&A/Cert/CenterOps │ └───────┬──────────┬───────┘
                    │                         │ • AI Tutor Proxy(JWT)│         │          │
                    │                         └───┬──────┬───────┬───┘         │          │
                    │                             │      │       │ (Signed     │          │
                    │        ┌────────────────────┘      │       │  lesson_    │          │
                    │        │ (Outbox Relay)            │       │  scope JWT) │          │
                    │        ▼                           │       ▼             │          │
                    │  ┌──────────────────────────────┐  │  ┌─────────────────────────┐   │
                    │  │ RabbitMQ / Amazon MQ + DLQ   │  │  │ ai-tutor (Python/FastAPI│   │
                    │  │ • oep.worker.notification.v2 │  │  │ + index_consumer/worker)│   │
                    │  │ • oep.worker.video (HLS)     │  │  │ • Socratic & Doc RAG    │   │
                    │  │ • oep.ai-tutor.index (+ DLQ) │  │  │ • Mistake Cache + P5    │   │
                    │  │ • exam-submission-queue [NEW]│  │  └────────┬────────┬───────┘   │
                    │  └──────────────┬───────────────┘  │           │        │           │
                    │                 ▼                  │           │        ▼           │
                    │  ┌──────────────────────────────┐  │           │  ┌──────────────┐  │
                    └─►│ worker-service :8083         │  │           │  │ External LLM │  │
                       │ • FFmpeg HLS 360p/720p       │  │           │  │ & Embedding  │  │
                       │ • Notification & Email + DLQ │  │           │  │ (Groq/Gemini │  │
                       │ • Exam Grader/Auto-Finalize  │  │           │  │  /OpenAI/Jina│  │
                       │ • Dormant 24m & Retention    │  │           │  └──────────────┘  │
                       └──────────────┬───────────────┘  │           ▼                    │
                                      │                  │  ┌─────────────────────────┐   │
         ┌────────────────────────────┼──────────────────┼──┤ ai-postgres (PG17 +     │   │
         ▼                            ▼                  ▼  │ pgvector, ADR-0015)     │   │
┌──────────────────────────┐  ┌──────────────────────────┐  │ • Source Chunks/Vectors │   │
│ Redis 7 / ElastiCache    │  │ PostgreSQL 16 (RDS /     │  │ • Conversations/Tokens  │   │
│ • Rate Limit/Revocation  │  │ Primary + Read Replica)  │  └─────────────────────────┘   │
│ • Cache + Stream Lock 30s│  │ • 46 bảng LMS (+3 legacy)│                                │
│ • Exam Drafts & Pre-warm │  │ • Outbox + Audit + Grades│                                │
│ • Mistake Cache + Panic  │  └──────────────────────────┘                                │
└──────────────────────────┘                                                              │
```

*(Lưu ý `[KẾ HOẠCH BỔ SUNG - MVP]` cho triển khai hạ tầng: bổ sung `ai-tutor` + `ai-postgres` vào `docker-compose.prod.yml`, Terraform `infra/aws/*` và GitHub Actions CI/CD; triển khai Prometheus/Grafana/Alarms trên AWS; cấu hình `aws_appautoscaling_scheduled_action` cho Pre-warming trước ca thi 15 phút.)*

## **8.2. Thiết kế Xử lý Sự cố Phụ thuộc (Failure Scenarios — Đã kiểm chứng Chaos + Bổ sung v1.4)**

| Kịch bản lỗi (Failure Scenario) | Hiện trạng đã kiểm chứng / Tác động | Cơ chế tự phục hồi & Giảm nhẹ (Mitigation & Fallback) |
| :--- | :--- | :--- |
| **1. Redis hoặc RabbitMQ dừng đột ngột** | `[ĐÃ TRIỂN KHAI]` Đã kiểm chứng qua `chaos.py` (`LOAD_TEST_REPORT.md`): **100% request thành công** trong lúc sự cố. | Redis: timeout 500ms, cache/rate-limit/idempotency/revocation chạy *fail-open*. RabbitMQ: sự kiện nằm an toàn trong bảng Transactional Outbox (`PENDING`) và tự động phát lại về `0` khi broker hồi phục. |
| **2. Một service (`core-lms` / `assessment`) hoặc DB chết** | `[ĐÃ TRIỂN KHAI]` Đã kiểm chứng qua `chaos.py`: lỗi cô lập hoàn toàn, service còn lại hoạt động **100%**. | Gateway mở Circuit Breaker trả `503` sạch kèm `Retry-After`; Nginx phân giải DNS động tự hồi phục sau ~9s; Hikari `connection-timeout = 3s` fail-fast không treo thread. |
| **3. Học viên rớt mạng giữa chừng hoặc đúng giây cuối ca thi** | `[KẾ HOẠCH BỔ SUNG - MVP]` Nếu không xử lý sẽ mất bài/bị 0 điểm. | Trình duyệt giữ trạng thái trên **IndexedDB** và tự đồng bộ bù; 60 giây cuối tăng tốc đồng bộ **5s/lần (Aggressive Sync)** lên Redis; quá `T_end + 30s`, **Server-side Auto-Finalize Worker** tự chấm từ bản nháp Redis mới nhất (0% mất bài). |
| **4. Dịch vụ LLM bên thứ ba bị lỗi (`Timeout / 429 / 503`)** | `[KẾ HOẠCH BỔ SUNG - MVP]` Hiện trả `503` làm gián đoạn trải nghiệm học. | Áp dụng **Hard Timeout 15–20s + Circuit Breaker**: **Cached Mistake Explanation** vẫn trả bình thường `< 50ms` cho ≥ 90% ca có trong Cache; **AI Tutor** tự động kích hoạt **Retrieval-only Fallback** trả về ngay **Top-3 đoạn học liệu/transcript liên quan nhất** từ `ai-postgres` (`pgvector`) mà không cần LLM sinh văn bản. |
| **5. Worker chấm thi / gửi Email bị sập hoặc SMTP lỗi** | `[ĐÃ TRIỂN KHAI]` cho In-app DLQ; `[KẾ HOẠCH BỔ SUNG - MVP]` sửa `EmailSender`. | Thông điệp nằm bền vững trên Persistent RabbitMQ (chỉ ACK sau khi ghi DB thành công + Idempotency Key); Email lỗi retry **3 lần** (Exponential Backoff) rồi đẩy vào **DLQ** để Admin kiểm tra/replay trên trang Ops, đồng thời thông báo In-app vẫn hiển thị tức thì. |
| **6. Đỉnh tải tăng đột biến gấp 5× (2.500 CCU mở đề cùng lúc)** | `[KẾ HOẠCH BỔ SUNG - MVP]` Quá tải kết nối Postgres primary nếu chấm đồng bộ. | Đọc đề 100% từ **Redis Cache (CQRS Read Model)**; nháp thi ghi Redis Delta Sync; nộp bài qua `exam-submission-queue`; **Scheduled Pre-warming** trước ca thi 15p; **Exam Panic Mode (Redis Circuit Breaker)** tự động ngắt tạm thời AI Tutor, Analytics và các tác vụ phụ khi CPU/DB > 80%. |

---

# **PHỤ LỤC A — BẢNG TỔNG HỢP TOÀN BỘ QUYẾT ĐỊNH ĐÃ CHỐT (BRD v1.4)**

| Mã | Hạng mục quyết định | Nội dung đã chốt chính thức trong BRD v1.4 (Đồng bộ 100% với `backend/BUSINESS.md`) | Trạng thái |
| :--- | :--- | :--- | :--- |
| **Q1** | **Đăng ký, Xác thực Email & Cấp tài khoản 3 vai trò** | Tự đăng ký (`PENDING_VERIFICATION` + OTP/Link 15–30p + Explicit Consent). Giữ 3 vai trò RBAC (`STUDENT`/`INSTRUCTOR`/`ADMIN`) + cờ `is_root_admin = true` cho 01 Root Admin bất khả xâm phạm. Admin tạo được cả 3 vai trò (chỉ Root Admin được tạo/khóa `ADMIN`); tài khoản do Admin tạo hộ miễn verify email nhưng bắt buộc đổi mật khẩu lần đầu. | `[ĐÃ TRIỂN KHAI]` đăng ký/consent/tạo GV-HV; `[KẾ HOẠCH BỔ SUNG - MVP]` Email verify & Root Admin |
| **Q2** | **Thời hạn học kết hợp, Archived Read-Only & 2 Rào chắn** | Hỗ trợ cấu hình thời hạn theo đợt (`startAt/endAt`), theo cá nhân (`access_period_days` 90/180 ngày) hoặc kết hợp cả 2 (`access_expires_at = min(...)`, Extension Code cộng thẳng vào `access_expires_at`). Hết hạn chuyển Archived Read-Only (xem tĩnh + Mini-quiz + Cache lỗi sai AI + đọc Q&A cũ; khóa quyền phát sinh chi phí; vá 2 bug BE/FE) kèm 2 rào chắn: 1 phiên/1 luồng + Fair-Use 15h/tháng và Dormant 24 tháng (NĐ 356/2025/NĐ-CP). | `[ĐÃ TRIỂN KHAI]` `CoursePhase` & `readOnly` cơ bản; `[KẾ HOẠCH BỔ SUNG - MVP]` Access Period, Archived đầy đủ & 2 rào chắn |
| **Q3** | **Giới hạn Thiết bị, 1 Luồng Video & Single-Device Exam Lock** | Tối đa 03 thiết bị đăng nhập (đá cũ nhất); khóa cứng 01 luồng phát video đồng thời qua Redis Heartbeat 30s; Single-Device Exam Lock + `Device Handover Audit Log` khi đang thi Post-test / Kỳ thi đồng loạt. | `[KẾ HOẠCH BỔ SUNG - MVP]` |
| **Q4** | **RBAC Biên soạn, Quyền GV & Ngoại lệ Root Admin** | Chỉ Admin tạo/sửa khóa học, LO, học liệu, Rubric, ngân hàng đề. Instructor chấm bài, Q&A, đăng `Announcement`, bấm `Flag Content/Rubric Issue`, sửa điểm câu tự luận/lỗi đề quiz có lý do + `AuditLog` (không sửa trắc nghiệm thi đồng loạt). Admin thường không chấm; chỉ **Root Admin** xử lý phúc khảo ngoại lệ cuối cùng kèm `AuditLog` mức cao khi môn chỉ còn 01 GV duy nhất. | `[ĐÃ TRIỂN KHAI]` khóa authoring & Announcement; `[KẾ HOẠCH BỔ SUNG - MVP]` Flag Issue & siết Root Admin |
| **Q5** | **LO Tùy chọn, Publish 2 lớp, Sequential (AND) & Fast-track** | Gắn LO/Bloom là **tùy chọn**; Publish Validation 2 lớp (Lớp bắt buộc mọi khóa + ≥ 2 GV cho khóa có tự luận; Lớp theo LO khi khóa có khai báo LO). Mặc định `SEQUENTIAL` (cho chọn `FREE_NAVIGATION`), toán tử AND (≥ 80% video tính từ Heartbeat 30s + anti-seek + đọc tài liệu VÀ ≥ 70% Mini-quiz). Fast-track Pre-test ≥ 85% theo LO (hoặc Module nếu không dùng LO), không thay Post-test. | `[ĐÃ TRIỂN KHAI]` nền Sequential + AND; `[KẾ HOẠCH BỔ SUNG - MVP]` LO tùy chọn, Publish 2 lớp, vá FE–BE, Heartbeat, Fast-track |
| **Q6** | **4 loại Quiz, Retake & Remediation 24h** | Phân 4 loại `PRE_TEST`/`MASS_EXAM` (`max=1`), `POST_TEST` (ẩn đáp án & giải thích AI động + Remediation 100% video Module & Cooldown 24h), `MINI_QUIZ` (`Unlimited`, gợi ý Socratic khi `< 70%`, trộn Pool theo LO/Module). | `[KẾ HOẠCH BỔ SUNG - MVP]` |
| **Q7** | **Kỳ thi Đồng loạt 500 CCU** | Giờ cố định `T_start`/`T_end`, vào muộn ≤ 15p, Hard Cutoff `T_end`, IndexedDB + Redis Delta Sync (20s / 5s ở 60s cuối), Client Jitter (0–10s), Grace Window 30s, Server-side Auto-Finalize từ Redis, Queue `W01` chấm bất đồng bộ, Cảnh báo > 500 CCU, Pre-warming 15p, Exam Panic Mode (Redis Circuit Breaker). | `[KẾ HOẠCH BỔ SUNG - MVP]` (đã có baseline load test 300 HV) |
| **Q8** | **Tự luận, Hàng đợi chấm, Rubric & Resubmit** | Rich-text/Markdown + PDF/DOCX ≤ 20 MB (`headObject` S3) trích xuất ≥ 50 từ / 300 ký tự; Blind Grading đồng nhất; Rubric từng tiêu chí; Claim/Lock 30p + Auto-extend Lease + `lock_token`. Cho Resubmit trước hạn trong `max_submissions` (kể cả sau khi `GRADED` → `PENDING_REGRADE`), chặn khi đang `CLAIMED`. | `[ĐÃ TRIỂN KHAI]` 20MB, Blind cơ bản, `@Version`, `PENDING_REGRADE`; `[KẾ HOẠCH BỔ SUNG - MVP]` Rubric, Claim/30p, PDF text-check |
| **Q9** | **Phúc khảo mở rộng & Cảnh báo > 15%** | Áp dụng cho cả Bài tự luận (`ASSIGNMENT_SUBMISSION`) và Lượt Quiz (`QUIZ_ATTEMPT`), tối đa 1 lần/mục trong 7 ngày; Blind Grading đồng nhất; định tuyến GV thứ 2 cùng môn (fallback Root Admin + AuditLog); ghi log & cảnh báo Admin nếu lệch > 15%. | `[ĐÃ TRIỂN KHAI]` 1x/7d; `[KẾ HOẠCH BỔ SUNG - MVP]` Blind phúc khảo, định tuyến & cảnh báo > 15% |
| **Q10** | **AI Tutor (RAG), Hạn mức, Mistake Cache & An toàn AI** | Document RAG 4 định dạng (`TEXT/PDF/MD/JSON`) + Access-Controlled RAG + Abstention `[ĐÃ TRIỂN KHAI]`; nâng cấp Prompt Socratic, `.docx`/`.vtt` tùy chọn + Timestamp `±90s`, khóa AI xuyên khóa theo `user_id` (mở Mini-quiz), quota **20 lượt/ngày**, câu hỏi **≤ 1.000 ký tự**, ngữ cảnh **3 lượt (6 tin nhắn)**, **Cached Mistake Explanation** (nạp sẵn + lazy-cache, mở cho Archived), XML Delimiters, TTFT < 3s / Hard Timeout 15–20s + Circuit Breaker + **Retrieval-only Fallback (Top-3)**, Eval P5 & lưu Token FinOps `[KẾ HOẠCH BỔ SUNG - MVP]`. Provider-agnostic (`Groq/Gemini/OpenAI`, `Jina/OpenAI/Gemini`). | `[ĐÃ TRIỂN KHAI]` nền RAG & adapter; `[KẾ HOẠCH BỔ SUNG - MVP]` các nâng cấp & P5 |
| **Q11** | **Phân tách Core MVP vs. Stretch Goal** | **Core MVP:** *Document & Timestamp-Aware Socratic AI Tutor*, *Cached Mistake Explanation*, *Exam Panic Circuit Breaker*, *Eval Pipeline P5*, *Open Badges 3.0 & CSAT/NPS*, *Tiếng Việt mặc định + WCAG 2.1 AA cơ bản*. **Stretch Goal (`[STRETCH]`):** *AI Quiz Generator (Admin-in-the-loop)*, *Song ngữ Tiếng Anh (i18n)*, *Đăng nhập Google OAuth*, *AI Viva Voce*, *AI Pre-Grading*, *Rewind Heatmap & Misconception Radar*, *Semantic FAQ Deflection & Verified Q&A → RAG*, *FSRS Spaced Repetition & Adaptive Remediation Playlist*. | `[QĐ]` |
| **Q12** | **HLS 360p/720p, `.vtt` Tùy chọn & Bảo mật Edge HLS** | Async FFmpeg HLS **360p/720p** `[ĐÃ TRIỂN KHAI]`; phụ đề `.vtt` là **tùy chọn** (`[KẾ HOẠCH BỔ SUNG - MVP]` cho UI/track/parser AI). Giai đoạn 1 bảo vệ bằng S3 Presigned Playlist 600s `[ĐÃ TRIỂN KHAI]`; nâng cấp Edge Signed Cookies + Dynamic Watermark đang nghiên cứu **2 phương án `[TBD]`** (PA-A: AWS CloudFront OAC + Signed Cookies vs. PA-B: Hybrid Cloudflare R2 + Worker HMAC \$0 Egress). | `[ĐÃ TRIỂN KHAI]` HLS 360p/720p; `[ĐANG NGHIÊN CỨU - TBD]` Edge Auth |
| **Q13** | **Chứng nhận Open Badges 3.0, Analytics, Q&A & Email DLQ** | Xét hoàn thành 100% Module + trọng số bài thi chính thức (`weight_% = 100%`, loại Pre-test) ≥ 70% + **CSAT/NPS** → **1EdTech Open Badges 3.0** (JSON-LD + chữ ký + QR/URL công khai). Bổ sung Drop-off từng bài, Learning Gain, LO Map, FinOps AI, Q&A mốc video, và Email Retry 3x + DLQ. | `[ĐÃ TRIỂN KHAI]` verify URL & báo cáo khóa; `[KẾ HOẠCH BỔ SUNG - MVP]` phần nâng cấp |
| **Q14** | **Tuân thủ Pháp lý (Luật 91/2025 & NĐ 356/2025)** | Explicit Consent, Export JSON cá nhân, Admin ẩn danh hóa PII `[ĐÃ TRIỂN KHAI]`; bổ sung luồng học viên tự yêu cầu khóa/xóa tài khoản & Worker Dormant 24 tháng `[KẾ HOẠCH BỔ SUNG - MVP]`. | `[ĐÃ TRIỂN KHAI]` một phần; `[KẾ HOẠCH BỔ SUNG - MVP]` self-delete & Dormant |
| **Q15** | **Kiến trúc Hệ thống & Hạ tầng AWS + FinOps** | **Modular Service-Oriented Architecture** (4 Java services + Python `ai-tutor` + 2 workers), **PostgreSQL 16 (Primary + Replica)** + **`ai-postgres` PG17 `pgvector` (`ADR-0015`)**, hạ tầng **AWS Terraform** (`ec2-demo` & `ecs-managed`, CI/CD GitHub Actions) `[ĐÃ TRIỂN KHAI]`. Bổ sung đưa `ai-tutor` + `ai-postgres` vào prod/Terraform/CI-CD, Scheduled Pre-warming 15p, Observability AWS & siết alert `p95 < 300ms` `[KẾ HOẠCH BỔ SUNG - MVP]`. | `[ĐÃ TRIỂN KHAI]` core AWS & 4+1 services; `[KẾ HOẠCH BỔ SUNG - MVP]` AI deploy, Pre-warming, Observability AWS |
| **Q16** | **05 Tính năng Vận hành Trung tâm (Center Operations)** | Chính thức đưa vào BRD v1.4 `[ĐÃ TRIỂN KHAI]`: (1) Sổ cái học phí thu tay/công nợ/hoàn tiền/miễn giảm/biên lai *(Out-of-Scope cổng thanh toán online)*; (2) Lịch học theo buổi & Điểm danh *(tùy chọn theo khóa)*; (3) Danh sách chờ (`WAITLISTED`) FIFO; (4) Điều kiện tiên quyết (BFS); (5) Ghi danh hàng loạt email (`Bulk Enroll`) & Miễn trừ (`Waiver`). | `[ĐÃ TRIỂN KHAI]` |
