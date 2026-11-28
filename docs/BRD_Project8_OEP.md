# **TÀI LIỆU YÊU CẦU NGHIỆP VỤ (BRD) — Phiên bản 1.3 (Bản Chốt Chính Thức & Đồng Bộ Toàn Diện P1–P2)**

**Đề tài:** Project 8 — Nền tảng Học trực tuyến và Luyện thi tích hợp AI (Online Education Platform \- OEP)

**Học phần:** Cloud Application Development (Giai đoạn P1 & P2 — Business, Data & Application Architecture)

**Quy mô hướng tới:** Production-oriented MVP (1.000 – 2.000 học viên · 5–7 sinh viên · 12 tuần)

**Ngày cập nhật:** 09/11/2026

**Quy ước đánh dấu:** \[QĐ\] \= Quyết định nghiệp vụ và kiến trúc đã được chốt chính thức (bao gồm cả 07 quyết định làm rõ từ SRS). \[HIỆU ĐÍNH v1.3\] \= Điểm đã được chuẩn hóa lại từ bản v1.2 để khép kín lỗ hổng nghiệp vụ/kỹ thuật theo chuẩn LMS quốc tế và khung kiến trúc P2. \[ĐỘT PHÁ \- MVP\] \= Tính năng khác biệt hóa cao thuộc phạm vi cam kết của MVP (tận dụng trực tiếp hạ tầng RAG, Redis, Queue sẵn có). \[STRETCH\] \= Tính năng mở rộng (Stretch Goal), chỉ triển khai khi hoàn thành sớm Core MVP.

# **1\. TỔNG QUAN DỰ ÁN & ĐỊNH VỊ OEP**

## **1.1. Bối cảnh & Bài toán Nghiệp vụ**

Đối tác là một trung tâm đào tạo sở hữu giáo trình chuẩn hóa nội bộ, quy mô **1.000 – 2.000 học viên đang hoạt động** (mỗi khóa **300 – 500 học viên**). Học viên học 100% từ xa theo lộ trình chuẩn hóa dựa trên năng lực đầu ra (Competency-Based / Mastery Learning), làm bài đánh giá năng lực định kỳ và được trợ lý AI hỗ trợ theo phương pháp Socratic. Hệ thống phục vụ **một đơn vị sở hữu duy nhất (Closed-Cohort B2B2C / Single-Tenant LMS)**, không phải chợ khóa học thương mại tự do (Open Marketplace).

## **1.2. Đối chiếu Tiêu chuẩn Tối thiểu của một OEP & Yêu cầu Học phần**

Thiết kế đáp ứng đầy đủ 8 Use Cases bắt buộc của Project 8 (Course management, Enrollment, Video/document learning content, Learning progress, Quiz/assessment, Assignment submission, Instructor dashboard, Notifications) và đối chiếu với các nền tảng giáo dục chuẩn quốc tế:

| Nhóm tiêu chuẩn | Yêu cầu cốt lõi | Thiết kế áp dụng trong MVP (Đã chốt \[QĐ\] & \[HIỆU ĐÍNH v1.3\]) | Đối chiếu nền tảng thực tế |
| :---- | :---- | :---- | :---- |
| **1\. Người dùng & Vai trò** | ≥ 3 vai trò, xác thực \+ RBAC | Learner (**tự đăng ký tài khoản Self-registration \+ xác thực Email**), Instructor và Admin (**do Admin cấp phát nội bộ**); xác thực tập trung (JWT \+ Refresh Token), kiểm soát RBAC trên 100% API \[QĐ\]. | [Canvas Course Role Permissions](https://community.instructure.com/en/kb/articles/387080-canvas-course-role-permissions) |
| **2\. Quản lý nội dung** | Phân cấp Course → Module → Lesson; Video, tài liệu, Quiz | **Chỉ Admin** thiết kế/nhập cấu trúc Course → Module → Lesson, gắn **Learning Outcome (LO)**, nạp học liệu (.mp4 ≤ 2 GB, .vtt ≤ 5 MB, PDF/DOCX ≤ 20 MB), quản lý Ngân hàng câu hỏi (bao gồm **độc quyền duyệt câu hỏi do AI sinh ra**), cấu hình Rubric và cấu hình tỷ trọng điểm xét tốt nghiệp (weight\_% \= 100%) \[QĐ\]. | [Coursera Mastery Learning](https://blog.coursera.org/how-to-integrate-mastery-learning-into-course-design/) |
| **3\. Học tập & Ghi danh** | Ghi danh, truy cập từ xa, theo dõi tiến độ | **Ghi danh bằng 1 Mã kích hoạt (Active Code) dùng 1 lần, cấp quyền truy cập toàn khóa** \[QĐ\]; sau khi hết Access Period, tài khoản chuyển sang **Chế độ Lưu trữ Xem tĩnh (Archived Read-Only Mode)** trọn vòng đời khóa học kèm chính sách chống tài khoản ngủ đông. | [edX Verified Archived Access](https://support.edx.org/hc/en-us/articles/360013318054-Does-upgrading-to-the-verified-track-extend-my-access-to-the-course), [Udemy Lifetime Access](https://support.udemy.com/hc/en-us/articles/229603708-Lifetime-Access) |
| **4\. Đánh giá & Khảo thí** | Quiz, bài tập, chấm điểm, phản hồi | Pre/Post-test (kèm cơ chế **Học bù mở lượt thi lại Remediation Attempt \+ Cooldown 24h**), Mini-quiz (bốc đề ngẫu nhiên từ Pool theo LO), Kỳ thi đồng loạt (Hard Cutoff \+ Aggressive Sync 5s \+ Server Grace Window 30s), bài tự luận chấm ẩn danh theo Rubric và phúc khảo độc lập. | [Moodle Quiz Settings](https://docs.moodle.org/en/Quiz_settings), [Gradescope Rubrics & Regrades](https://guides.gradescope.com/hc/en-us/articles/22249389005709-Grading-submissions-with-rubrics) |
| **5\. Tương tác học thuật** | ≥ 1 kênh thảo luận | **Q\&A Thread** gắn theo từng bài học và mốc giây video (lược bỏ Live Chat 1-1). | [Udemy Q\&A](https://teach.udemy.com/organize-with-featured-questions/) |
| **6\. Chứng nhận** | Xác nhận hoàn thành / chứng nhận | Tự động xét hoàn thành dựa trên tiến độ 100% Module \+ điểm trung bình trọng số **Post-test / Thi đồng loạt / Tự luận ≥ 70%** (tỷ trọng do Admin cấu hình; không lấy điểm Pre-test thay thế) và xuất **Digital Certificate chuẩn [1EdTech Open Badges 3.0](https://www.imsglobal.org/spec/ob/v3p0/cert)** \[HIỆU ĐÍNH v1.3\]. | [Coursera Honor Code](https://www.coursera.support/s/article/learner-000001214), [Credly Open Badge 3.0](https://info.credly.com/product/open-badge-3.0) |
| **7\. Thanh toán** | Chỉ bắt buộc với mô hình thương mại tự do | **Out-of-Scope:** học phí thu ngoài hệ thống tại trung tâm; hệ thống chỉ tiếp nhận Mã kích hoạt do Admin phát hành. Không lưu trữ dữ liệu thẻ/ngân hàng. | Mô hình B2B Enrollment Voucher |

# **2\. CÁC BÊN LIÊN QUAN & PHÂN QUYỀN (RBAC)**

| Vai trò | Quy mô | Quyền hạn & Trách nhiệm (Đã chốt \[QĐ\] & \[HIỆU ĐÍNH v1.3\]) |
| :---- | :---- | :---- |
| **1\. Học viên (Learner)** | 1.000 – 2.000 (300 – 500/khóa) | • **Tự đăng ký tài khoản (Self-registration \+ xác thực Email \+ Explicit Consent)**, đăng nhập và nhập **Mã kích hoạt** để ghi danh toàn bộ khóa học \[QĐ\]. • Học Video HLS, PDF/DOCX kèm phụ đề/transcript (.vtt); theo dõi tiến độ và Bản đồ thành thạo LO; nhận chứng nhận số chuẩn Open Badges 3.0. • Làm Pre/Post-test, Mini-quiz, nộp bài tự luận (gõ trực tiếp hoặc tải file PDF/DOCX dạng văn bản ≤ 20 MB/tệp), tham gia kỳ thi đồng loạt. • Đặt câu hỏi ở Q\&A Thread; hỏi **AI Tutor ngữ cảnh theo mốc giây video** trong hạn mức 20 lượt/ngày (bị khóa khi đang trong phiên Pre-test, Post-test và thi đồng loạt). |
| **2\. Giảng viên (Instructor)** | 15 – 30 (Do Admin cấp nội bộ) | • **Nhận (Claim) và chấm** bài tự luận từ Hàng đợi chấm chung theo Rubric ẩn danh, gửi phản hồi; chấm phúc khảo độc lập (khi được hệ thống định tuyến là Instructor thứ 2). • Giải đáp Q\&A học thuật cho học viên. • Xem Dashboard Learning Analytics của khóa học (Completion, Drop-off, phân bố điểm) và bấm gắn cờ báo lỗi (Flag Content/Rubric Issue) gửi Admin khi phát hiện sai sót trong lúc chấm bài hoặc giải đáp Q\&A. • **Không** tạo/sửa cấu trúc khóa học, LO, học liệu gốc, Rubric hay kiểm duyệt Ngân hàng câu hỏi \[QĐ\] (tập trung 100% vào chuyên môn chấm bài và hỗ trợ học viên). |
| **3\. Quản trị viên (Admin)** | 3 – 5 nhân sự học vụ (Do Admin cấp nội bộ) | • Khởi tạo và quản lý tài khoản nội bộ cho Instructor và Admin \[QĐ\]. • Thiết kế cấu trúc Course → Module → Lesson, gắn LO, tải học liệu (.mp4 ≤ 2 GB, .vtt ≤ 5 MB, PDF/DOCX ≤ 20 MB), cấu hình Rubric, **cấu hình tỷ trọng điểm xét tốt nghiệp (weight\_%, tổng \= 100%)**, cài đặt số lần làm lại (max\_attempts) và phát hành khóa học (Publish) \[QĐ\]. • Sử dụng **AI Quiz Generator** và **độc quyền trực tiếp kiểm duyệt (Approve / Edit / Reject)** câu hỏi do AI sinh ra để đưa vào Ngân hàng câu hỏi \[QĐ\]. • Phát hành và quản lý vòng đời **Mã kích hoạt** (tạo lô CSV/QR, thu hồi, cấp lại, gia hạn). • Lên lịch thi đồng loạt (kèm Cảnh báo cân bằng tải); cấu hình thời hạn quyền (Access Period), hạn mức AI, ngưỡng qua môn, ngưỡng Fast-track. • Giám sát Dashboard vận hành, cảnh báo lệch điểm phúc khảo (\> 15%), sức khỏe hệ thống (SLI/SLO), chi phí Cloud/AI (FinOps), báo cáo tuân thủ dữ liệu. |

# **3\. LUỒNG NGHIỆP VỤ CỐT LÕI**

## **3.1. Thiết kế Khóa học bám sát Chuẩn đầu ra (Constructive Alignment) & Xử lý Học liệu \[QĐ\]**

1. **Cấu trúc chuẩn:** Course → Module (Chuyên đề) → Lesson. Module là đơn vị chuyên đề học thuật, độc lập với phương thức đóng phí.  
2. **Gắn nhãn Chuẩn đầu ra (LO):** Mỗi Module/Lesson có **Learning Outcome (LO)** rõ ràng; mọi Video/tài liệu, Quiz, Assignment và câu hỏi do AI sinh đều bắt buộc gắn mã LO tương ứng cùng mức nhận thức theo thang Bloom rút gọn (Nhớ / Hiểu / Vận dụng), tương đồng mô hình [Coursera Mastery Learning](https://blog.coursera.org/how-to-integrate-mastery-learning-into-course-design/).  
3. **Validation Rule khi phát hành (\[HIỆU ĐÍNH v1.3\]):** Khóa học không thể chuyển sang trạng thái Published nếu: (a) Tổng tỷ trọng điểm các bài kiểm tra chính thức (Post-test \+ Kỳ thi đồng loạt \+ Bài tự luận) khác 100%, hoặc (b) Tồn tại bất kỳ LO nào chưa có tối thiểu **01 học liệu**, tối thiểu **03 câu hỏi Mini-quiz** (để phục vụ trộn đề khi làm lại) và tối thiểu **01 câu hỏi đánh giá thuộc bài kiểm tra chính thức**.  
4. **Luồng tải Video & Phụ đề bất đồng bộ (Async Media Pipeline):**  
   * Admin tải file video gốc (.mp4 ≤ 2 GB/tệp) kèm file phụ đề chuẩn (.vtt hoặc .srt ≤ 5 MB/tệp có sẵn của trung tâm) và tài liệu (PDF/DOCX ≤ 20 MB/tệp) trực tiếp lên Object Storage ([Cloudflare R2](https://www.cloudflare.com/products/r2/)) thông qua **Presigned Upload URL** ([Cloudflare R2 Presigned URLs](https://developers.cloudflare.com/r2/api/s3/presigned-urls/) — không đẩy luồng file nhị phân lớn đi xuyên qua API Server).  
   * Sự kiện tải lên hoàn tất đẩy 1 job vào **Message Queue** để **Media Worker** đóng gói bất đồng bộ sang chuẩn **HLS (manifest .m3u8 \+ các phân đoạn .ts/.m4s ở độ phân giải 480p/720p)** và bóc tách file .vtt thành các phân đoạn văn bản gắn mốc giây (timestamped chunks) phục vụ tìm kiếm transcript và RAG.

## **3.2. Tự Đăng ký, Ghi danh bằng Mã kích hoạt (Active Code) & Phạm vi Quyền \[QĐ\]**

1. **Tự đăng ký tài khoản & Kích hoạt Mã (Active Code):** Học viên tự đăng ký tài khoản (Self-registration bằng Email \+ Mật khẩu, xác thực Email OTP/Link và xác nhận Explicit Consent), sau đó nhập Mã kích hoạt (dạng chuỗi ký tự hoặc quét mã QR) do Admin phát hành cho từng Course. Mỗi mã dùng **1 lần duy nhất**, gắn chặt với **1 tài khoản – 1 khóa học**. Giao dịch kích hoạt đảm bảo tính **Atomic & Idempotent** ở tầng cơ sở dữ liệu (UPDATE activation\_codes SET status \= 'USED', used\_by \= :uid WHERE code \= :code AND status \= 'AVAILABLE'), xử lý an toàn khi học viên bấm đúp hoặc hai thiết bị gửi yêu cầu cùng lúc.  
2. **Thời hạn học (Access Period) & Chuyển sang Chế độ Lưu trữ Xem tĩnh (Archived Read-Only Mode)** \[QĐ\] & \[HIỆU ĐÍNH v1.3\]:  
   * **Thời hạn học chính thức (Access Period):** Tính linh hoạt **N ngày kể từ thời điểm kích hoạt mã** (mặc định **90 hoặc 180 ngày** tùy khóa học do Admin cấu hình). Admin có thể phát hành **Mã gia hạn (Extension Code)** để cộng thêm ngày học cho tài khoản hiện có.  
   * **Tách quyền sau khi hết Access Period** (tham chiếu mô hình [edX Verified Archived Access](https://support.edx.org/hc/en-us/articles/360013318054-Does-upgrading-to-the-verified-track-extend-my-access-to-the-course) và chính sách vòng đời khóa học của [Udemy Lifetime Access](https://support.udemy.com/hc/en-us/articles/229603708-Lifetime-Access)):

| Nhóm quyền | Nội dung chi tiết | Hiệu lực truy cập & Rào chắn chống lạm dụng (Guardrails) |
| :---- | :---- | :---- |
| **Quyền xem lại học liệu lưu trữ (Archived Static Access)** | Xem Video HLS, đọc tài liệu PDF/DOCX, làm lại Mini-quiz tự luyện (chấm tự động bằng đáp án có sẵn, xem lại lời giải thích lỗi sai của AI **đã có sẵn trong Cache** không tốn phí LLM mới) | **Vĩnh viễn theo vòng đời khóa học** \[QĐ\], kèm **2 rào chắn bảo vệ thực tế:** 1\. *Chống sang nhượng tài khoản & FinOps:* Sau khi hết hạn học chính thức, tài khoản tĩnh giới hạn **1 phiên đăng nhập / 1 luồng phát đồng thời** và hạn mức hợp lý **15 giờ phát video/tháng (54.000 giây)**. 2\. *Tuân thủ pháp lý dữ liệu (Dormant Policy):* Nếu tài khoản không đăng nhập liên tục trong **24 tháng**, hệ thống tự động xóa/ẩn danh hóa log hành vi (Heartbeat) theo Nghị định 356/2025/NĐ-CP và đưa tài khoản vào trạng thái ngủ đông (Dormant — cần xác thực OTP qua email gốc để mở lại). |
| **Quyền tương tác phát sinh chi phí (Active Paid-Cost Access)** | Chat trực tiếp với AI Tutor, yêu cầu AI sinh lời giải mới ngoài cache, nộp bài tự luận vào Hàng đợi chấm của Instructor, tham gia Kỳ thi đồng loạt, làm Post-test lấy chứng chỉ, đăng câu hỏi mới lên Q\&A | **Chỉ có hiệu lực trong Thời hạn học (Access Period)**; khi hết hạn sẽ tự động khóa (học viên vẫn đọc được các thảo luận Q\&A cũ). |

3.   
   **Kiểm soát Thiết bị Đăng nhập & Khóa Cứng 01 Luồng Phát Video (Session & Active Stream Control)** \[QĐ\] & \[HIỆU ĐÍNH v1.3\]:  
   * Trong Access Period, hệ thống cho phép học viên lưu phiên đăng nhập trên tối đa **03 thiết bị cá nhân** (đăng nhập trên thiết bị thứ 4 sẽ tự động thu hồi phiên thiết bị cũ nhất), nhưng giới hạn cứng **tối đa 01 luồng phát video HLS hoạt động (1 Concurrent Active Media Stream)** tại một thời điểm thông qua khóa phiên Heartbeat 30 giây trên Redis.  
   * **Khóa đơn thiết bị khi đang thi (Single-Device Exam Lock):** Nếu tài khoản đang có phiên làm bài ACTIVE trong Kỳ thi đồng loạt hoặc Post-test, hệ thống **chặn đăng nhập/mở đề trên thiết bị mới** (trừ khi học viên xác nhận hủy phiên trên thiết bị cũ bị sập nguồn có ghi log kiểm toán Device Handover Audit Log) để chống thi hộ.  
4. **Lộ trình học — Sequential Mastery (Toán tử VÀ) \+ Fast-track an toàn** \[QĐ\] & \[HIỆU ĐÍNH v1.3\]:  
   * **Mặc định (Sequential Mastery — Toán tử VÀ / AND):** Học viên chỉ mở khóa Lesson kế tiếp khi hoàn thành **ĐỒNG THỜI cả 2 điều kiện** tại Lesson trước: xem **≥ 80% thời lượng video \+ đọc tài liệu VÀ đạt ≥ 70% điểm Mini-quiz cuối bài** (khắc phục triệt để lỗ hổng dùng HOẶC khiến học viên có thể đoán mò Mini-quiz để bỏ qua 100% video).  
   * **Fast-track qua Pre-test:** Học viên làm bài chẩn đoán đầu khóa (Pre-test) và đạt **≥ 85%** (Admin cấu hình được) ở các câu hỏi thuộc LO nào sẽ được **mở khóa ngay (Bypass Sequential Video Lock)** các Lesson thuộc LO đó mà không bắt buộc xem đủ 80% video lý thuyết. **Tuy nhiên, điểm Pre-test KHÔNG được dùng để thay thế bài kiểm tra cuối chuyên đề khi xét cấp chứng nhận** — học viên vẫn bắt buộc phải làm và đạt điểm tại các bài **Post-test / Kỳ thi chính thức** \[QĐ\].

## **3.3. Đánh giá, Hàng đợi Chấm bài & Chứng nhận \[QĐ\]**

### **A. Quy tắc Số lượt làm lại & Học bù Mở lượt thi (Configurable Retake & Remediation Policy) \[QĐ\] & \[HIỆU ĐÍNH v1.3\]**

Admin có quyền cài đặt số lượt làm bài tối đa (max\_attempts) và cách lấy điểm (Highest / Latest / Average) khi cấu hình từng bài kiểm tra trên hệ thống, **kèm 3 quy tắc bảo vệ tính toàn vẹn học thuật (Validation Rules):**

1. **Đối với Pre-test và Kỳ thi đồng loạt (Scheduled Mass Exam):** Hệ thống khóa cứng max\_attempts \= 1 (chỉ làm 1 lần duy nhất) để bảo toàn độ chính xác của chỉ số Learning Gain \= Post-test − Pre-test và chống làm thử đề để lấy Fast-track ảo.  
2. **Đối với Post-test (Bài kiểm tra tính điểm xét chứng nhận):**  
   * Admin cài đặt số lần làm lại (max\_attempts, khuyến nghị 1–3 lần). Nếu cho phép làm lại (max\_attempts \> 1), hệ thống **ẩn đáp án đúng chi tiết và ẩn nút "AI giải thích lỗi sai"** cho đến khi học viên đã **Đạt (Passed ≥ 70%)** hoặc đã hết lượt làm bài, đồng thời tự động trộn/bốc ngẫu nhiên câu hỏi cùng LO từ Ngân hàng câu hỏi ở lần làm lại.  
   * **Cơ chế Học bù mở lượt thi lại (Remediation Attempt — \[QĐ\]):** Nếu học viên sử dụng hết toàn bộ max\_attempts của Post-test mà vẫn chưa đạt \< 70% (khi vẫn còn trong Access Period), hệ thống chuyển bài kiểm tra sang trạng thái REMEDIATION\_REQUIRED và **tự động cấp thêm 01 lượt thi lại bổ sung** sau khi học viên hoàn thành xem lại đủ 100% thời lượng video của Module đó và vượt qua thời gian chờ **Cooldown 24 giờ**.  
3. **Đối với Mini-quiz tự luyện cuối mỗi Lesson (\[HIỆU ĐÍNH v1.3\]):** Mặc định max\_attempts \= Unlimited (không giới hạn số lần làm lại). Khi nộp bài chưa đạt \< 70%, hệ thống **chỉ hiển thị gợi ý Socratic theo khái niệm mà không lộ đáp án chữ cái trực tiếp (A/B/C/D)**, đồng thời trộn câu hỏi từ Pool (≥ 3 câu/LO) ở lượt làm tiếp theo; đáp án chi tiết và giải thích lỗi sai đầy đủ được mở ngay khi học viên đạt ≥ 70%.

### **B. Kỳ thi đồng loạt (Scheduled Mass Exam)**

* **Đồng hồ máy chủ & Tách ngưỡng Hard Cutoff / Aggressive Sync / Grace Window** \[QĐ\] & \[HIỆU ĐÍNH v1.3\] (tham chiếu cơ chế When time expires và Submission grace period của [Moodle Quiz](https://docs.moodle.org/en/Quiz_settings)):  
  * Ca thi có giờ đóng cố định (T\_end, ví dụ 21:00:00). Học viên vào muộn (tối đa **15 phút** kể từ giờ mở đề, Admin cấu hình được) chỉ được làm bài trong phần thời gian còn lại tới T\_end; quá 15 phút không được vào ca thi.  
  * **Mốc khóa thao tác (Hard Cutoff \= T\_end):** Đúng 21:00:00 theo đồng hồ máy chủ, giao diện làm bài bị khóa chọn đáp án; mọi thay đổi đáp án có interaction\_timestamp \> T\_end đều bị từ chối.  
  * **Đồng bộ gia tốc phút cuối (End-of-Exam Aggressive Sync — \[HIỆU ĐÍNH v1.3\]):** Trong **60 giây cuối trước T\_end**, chu kỳ đồng bộ nháp tự động rút ngắn từ 20 giây xuống **5 giây/lần** để giảm thiểu tối đa rủi ro mất đáp án vừa khoanh ở những giây cuối nếu đứt mạng đột ngột.  
  * **Cửa sổ ân hạn truyền tải (Server Grace Window \= T\_end \+ 30 giây):** Để hỗ trợ cơ chế **Client Jitter (trễ ngẫu nhiên 0–10 giây)** nhằm làm phẳng đỉnh tải mà không làm học viên bị từ chối bài nộp do quá giờ, máy chủ chấp nhận gói tin chốt bài (FINAL\_SUBMIT) trong khoảng \[T\_end, T\_end \+ 30s\] miễn là các câu trả lời bên trong được ghi nhận trước hoặc đúng T\_end.  
  * **Chốt bài tự động phía máy chủ (Server-side Auto-Finalize Fallback):** Quá mốc T\_end \+ 30s, nếu học viên bị đứt mạng hoàn toàn và client không gửi được gói tin FINAL\_SUBMIT, Worker máy chủ tự động lấy bản lưu nháp mới nhất đã đồng bộ trên **Redis** để chấm điểm, đảm bảo **0% học viên bị mất bài thi do sự cố mạng phút chót**.  
* **Lưu nháp 2 lớp (Dual-layer Auto-save):** Ghi tức thời tại trình duyệt (IndexedDB) ngay khi chọn đáp án; đồng bộ bất đồng bộ lên Redis của máy chủ định kỳ mỗi **20 giây** (và **5 giây** ở phút cuối) kết hợp **Dirty-state Delta Sync** (chỉ gửi request khi học viên có thay đổi đáp án để giảm 50–70% tải dư thừa).  
* **Nộp bài bất đồng bộ qua Hàng đợi (Queue as a Shock Absorber):** Yêu cầu nộp bài được đẩy vào **Message Queue** để Worker chấm điểm và tính toán thống kê; học viên nhận ngay biên nhận "Đã ghi nhận bài nộp lúc HH:MM:SS". Đáp án đúng tuyệt đối không gửi xuống trình duyệt trước khi ca thi kết thúc hoàn toàn.  
* **Cảnh báo cân bằng tải & Chế độ Cứu hộ Ca thi (Exam Panic / Circuit Breaker Mode)** \[ĐỘT PHÁ \- MVP\]:  
  * Khi lịch thi dự kiến vượt 500 CCU, hệ thống cảnh báo Admin và tự động lập lịch **Pre-warming** (tăng sẵn số lượng container instance và nạp đề thi vào Redis Cache trước giờ mở đề 15 phút).  
  * Nếu trong ca thi, tải CPU hoặc kết nối DB vượt ngưỡng an toàn (80%), **Circuit Breaker** (điều khiển qua cờ trạng thái trên Redis) tự động tạm dừng các tác vụ phụ (AI Tutor toàn hệ thống, AI Pre-Grading, thống kê Rewind Heatmap) để dành 100% tài nguyên cho luồng làm bài và nộp bài.

### **C. Bài tự luận & Hàng đợi chấm chung (Shared Grading Queue)**

* **Định dạng nộp, Giới hạn dung lượng & Kiểm tra an toàn tệp tin** \[QĐ\]:  
  * Chấp nhận văn bản gõ trực tiếp trên trình soạn thảo (Rich-text/Markdown) hoặc tệp **PDF/DOCX có dung lượng ≤ 20 MB/tệp và có lớp văn bản trích xuất được (Text-searchable)**.  
  * **Quy tắc xác thực tự động khi tải lên:** Hệ thống kiểm tra tệp phải trích xuất được tối thiểu **50 từ (hoặc ≥ 300 ký tự chữ cái hợp lệ)**; từ chối ngay tệp PDF chỉ chứa ảnh scan trắng (0 ký tự text) hoặc vượt quá 20 MB, nhưng **vẫn chấp nhận hợp lệ** nếu tệp DOCX/PDF văn bản có đính kèm biểu đồ/hình vẽ minh họa phụ trợ của học viên.  
* **Cơ chế Claim/Lock có gia hạn (Lease with Heartbeat) & Chấm ẩn danh (Blind Grading)** \[QĐ\]:  
  * Tham chiếu luồng chấm ẩn danh của [Canvas SpeedGrader](https://community.instructure.com/en/kb/articles/664277-how-do-i-hide-student-names-in-speedgrader) và chấm theo tiêu chí của [Gradescope](https://guides.gradescope.com/hc/en-us/articles/22249389005709-Grading-submissions-with-rubrics): Instructor không nhìn thấy họ tên/mã học viên khi chấm.  
  * Khi Instructor bấm "Nhận chấm (Claim)", bài làm được khóa trong **30 phút** kèm lock\_token. Khi Instructor đang thao tác gõ nhận xét trên màn hình, trình duyệt gửi tín hiệu gia hạn khóa tự động; khi bấm lưu điểm, Backend kiểm tra lock\_token (Optimistic Concurrency Control) để loại bỏ hoàn toàn lỗi 2 giảng viên chấm đè lên nhau.  
* **Nộp lại & Phúc khảo (Resubmit & Regrade)** \[QĐ\]:  
  * Cho phép học viên nộp lại (Resubmit) trước hạn nộp nếu bài chưa chuyển sang trạng thái CLAIMED.  
  * Học viên được quyền gửi yêu cầu **Phúc khảo (Regrade Request)** tối đa **1 lần/bài trong vòng 7 ngày** kể từ khi công bố điểm; hệ thống tự động định tuyến bài phúc khảo cho một Instructor thứ 2 độc lập. **Điểm của Instructor chấm phúc khảo là điểm quyết định cuối cùng**, đồng thời hệ thống ghi log độ lệch điểm giữa 2 lần chấm và gửi cảnh báo giám sát chất lượng cho Admin nếu độ lệch vượt \> 15%.  
* **AI Pre-Grading & Vấn đáp Xác thực (\[STRETCH\]):** Worker bất đồng bộ đối chiếu văn bản bài làm với từng tiêu chí trong Rubric để gợi ý mức điểm nháp và tô sáng ý đạt/thiếu (Evidence Highlighting), kết hợp sinh 2 câu hỏi vấn đáp ngắn (AI Viva Voce) để kiểm tra tính xác thực tác giả; **Instructor luôn là người quyết định điểm cuối cùng**.

### **D. Điều kiện Hoàn thành, Trọng số Cấu hình & Cấp Chứng nhận Số (Digital Certificate) \[QĐ\] & \[HIỆU ĐÍNH v1.3\]**

* Học viên được cấp chứng nhận số chuẩn quốc tế [**1EdTech Open Badges 3.0**](https://www.imsglobal.org/spec/ob/v3p0/cert) (kèm chữ ký mật mã JSON-LD và mã tra cứu xác thực QR/URL công khai) khi đạt đồng thời 2 điều kiện trong Thời hạn học (Access Period):  
  1. Hoàn thành **100% các Module bắt buộc** (bao gồm các Lesson đã học tuần tự hoặc được mở khóa qua Fast-track).  
  2. Điểm trung bình trọng số của các **Bài kiểm tra chính thức (Post-test, Kỳ thi đồng loạt, Bài tập tự luận bắt buộc)** tính theo **tỷ trọng weight\_% (tổng \= 100%) do Admin cấu hình cho khóa học đó** đạt **≥ 70%** (Admin cấu hình được ngưỡng qua môn; **tuyệt đối không lấy điểm Pre-test thay thế cho Post-test**).

## **3.4. AI Tutor (RAG) & Kiểm định Chất lượng AI \[QĐ\]**

1. **AI Tutor phương pháp Socratic gắn ngữ cảnh thời gian thực (Timestamp-Aware Socratic Tutor)** \[QĐ\] \+ \[ĐỘT PHÁ \- MVP\]:  
   * Lấy cảm hứng từ [Khanmigo](https://www.khanmigo.ai/), [CS50.ai Duck Debugger](https://cs50.readthedocs.io/cs50.ai/) và [Coursera Coach](https://blog.coursera.org/coursera-coach-leveraging-genai-to-empower-learners/): AI Tutor hướng dẫn học viên tự tư duy qua câu hỏi gợi mở thay vì giải hộ bài tập.  
   * **Kiểm soát phạm vi truy xuất (Access-Controlled RAG):** Chỉ truy xuất các đoạn học liệu (chunks) thuộc những **Lesson mà học viên đã được mở khóa**, kèm **trích dẫn chính xác** (số trang PDF/DOCX hoặc mốc MM:SS của video từ file .vtt).  
   * **Hỏi nhanh theo mốc dừng video (One-Click Timestamp Context):** Khi học viên đang xem bài giảng và bấm *"Hỏi AI tại mốc này"*, hệ thống tự động đính kèm (lesson\_id, current\_video\_timestamp) cùng đoạn transcript ±90 giây quanh mốc đó vào ngữ cảnh, giúp AI giải thích trúng ngay khái niệm trên màn hình và trả về nút bấm nhảy thẳng tới mốc video liên quan.  
2. **Khóa AI Tutor chống gian lận ở tầng Backend** \[QĐ\]:  
   * Tự động từ chối mọi yêu cầu gọi API AI Tutor và AI Giải thích lỗi sai khi user\_id đang có phiên làm bài ACTIVE thuộc **Pre-test, Post-test hoặc Kỳ thi đồng loạt** (chặn triệt để việc mở tab thứ 2 hoặc đăng nhập trên điện thoại để hỏi AI).  
   * **Mini-quiz tự luyện** vẫn cho phép hỏi gợi ý kiểu Socratic.  
3. **Giới hạn tần suất & Kiểm soát chi phí (FinOps Guardrail)** \[QĐ\]:  
   * Hạn mức mặc định **20 lượt hỏi/học viên/ngày**, độ dài câu hỏi **≤ 500 ký tự**, giới hạn ngữ cảnh hội thoại (Context Window) tối đa 5 lượt gần nhất; chỉ áp dụng cho học viên còn trong Access Period.  
4. **AI Giải thích lỗi sai có bộ nhớ đệm (Cached Mistake Explanation)** \[QĐ\] \+ \[ĐỘT PHÁ \- MVP\]:  
   * Tham chiếu tính năng giải thích lỗi sai tức thời của [Duolingo Explain My Answer](https://www.classcentral.com/report/chatgpt-on-duolingo-khan-academy/): sau khi hoàn thành Mini-quiz (hoặc sau khi đã Đạt/hết lượt làm Post-test và khi ca thi đồng loạt đã đóng), học viên bấm *"Tại sao tôi chọn sai?"*.  
   * Hệ thống lưu Cache lời giải thích theo khóa kết hợp (question\_id, selected\_option\_id, prompt\_version) (được làm giàu sẵn ngay từ bước Admin duyệt câu hỏi AI). Toàn bộ các học viên chọn sai đáp án đó (kể cả học viên đang ở chế độ Xem lại học liệu tĩnh) nhận kết quả tức thì (\< 50ms) với **chi phí Token \= 0** (kỳ vọng Cache Hit Rate đạt **≥ 85% – 90%**).  
5. **AI Quiz Generator (Admin-in-the-loop) & Đánh giá chất lượng AI (P5)** \[QĐ\]:  
   * AI hỗ trợ sinh câu hỏi trắc nghiệm nháp theo từng LO và mức độ Bloom kèm lời giải thích cho từng phương án nhiễu → **chỉ Admin độc quyền trực tiếp kiểm duyệt (Approve / Edit / Reject)** trước khi đưa vào Ngân hàng câu hỏi \[QĐ\].  
   * Bộ kiểm thử tự động đánh giá RAG theo yêu cầu P5: đo Recall@K, Groundedness (độ bám sát tài liệu), Out-of-scope Refusal Rate (tỷ lệ từ chối đúng câu hỏi ngoài phạm vi), kiểm thử chống Indirect Prompt Injection, độ trễ và chi phí token/request.  
6. **Các tính năng Mở rộng (\[STRETCH\] — Triển khai nếu còn thời gian sau khi xong Core MVP):**  
   * *AI Viva Voce (Vấn đáp xác thực tác giả bài tự luận):* Sinh 2 câu hỏi ngắn xoáy vào lập luận trong bài nộp PDF/DOCX của học viên để tính điểm Viva Consistency Score hỗ trợ giảng viên chấm bài.  
   * *Semantic FAQ Deflection & Verified Q\&A → RAG Auto-Ingestion:* Gợi ý câu hỏi trùng lặp khi gõ Q\&A và tự động nhúng câu trả lời đã được xác nhận vào Vector DB.  
   * *FSRS Spaced Repetition & Adaptive Micro-Remediation Playlist:* Ứng dụng thuật toán [FSRS](https://github.com/open-spaced-repetition/awesome-fsrs) / [Duolingo Half-Life Regression](https://github.com/duolingo/halflife-regression) để tạo gói 5 câu hỏi ôn tập 3 phút/ngày và danh sách cắt đoạn video cần xem lại cho các LO dưới 70%.

# **4\. TIÊU CHUẨN CHẤT LƯỢNG & TUÂN THỦ**

## **4.1. Sư phạm & Hiệu quả học tập \[QĐ\]**

1. **Constructive Alignment:** 100% bài học, bài tập tự luận và câu hỏi trắc nghiệm ánh xạ trực tiếp tới LO.  
2. **Learning Analytics:**  
   * **Core MVP:** Theo dõi Completion Rate, Drop-off Rate theo từng bài, Learning Gain (Điểm Post-test − Điểm Pre-test), Bản đồ thành thạo LO cơ bản (tỷ lệ % đạt theo từng LO), và khảo sát CSAT/NPS trước khi xuất chứng chỉ.  
   * **Stretch Goal (\[STRETCH\]):** **Rewind Heatmap & Misconception Radar** (thống kê các phân đoạn video bucket 30 giây bị học viên tua lại nhiều nhất và phương án nhiễu bị chọn sai nhiều nhất) cùng **Adaptive Remediation Playlist**.

## **4.2. Chất lượng Kỹ thuật (ISO/IEC 25010 & NFRs) \[QĐ\] & \[HIỆU ĐÍNH v1.3\]**

| Đặc tính | Chỉ số mục tiêu (NFR Targets) | Giải pháp kiến trúc & Kỹ thuật |
| :---- | :---- | :---- |
| **Performance Efficiency** | • Chịu tải **≥ 500 CCU** trong ca thi đồng loạt. • **Tải duy trì khi đang thi:** \~25–60 RPS (nhờ Dirty-state Auto-save mỗi 20s, tăng tốc lên 5s ở 60s cuối). • **Tải đỉnh khi mở đề / nộp bài:** \~150–200 RPS, được làm phẳng qua Cache \+ Client Jitter (0–10s) \+ Message Queue. • Độ trễ API nghiệp vụ (đọc/ghi thông thường): **p95 \< 300 ms**. • Khởi tạo phát Video HLS: **\< 2,0 s**. • Thời gian phản hồi token đầu tiên của AI Tutor (TTFT): **\< 3,0 s** (Cache lỗi sai **\< 50 ms**). | • Lưu trữ học liệu trên Object Storage không phí băng thông ra ([Cloudflare R2](https://developers.cloudflare.com/r2/pricing/)) kết hợp Cloudflare CDN (Metadata → DB, Actual File → Object Storage). • Cache đề thi đã trộn sẵn theo nhóm mã đề trên **Redis** (CQRS Read Model). • Đẩy tác vụ chấm điểm, gửi thông báo, xử lý video và gọi AI sang **Async Worker Queue** (Queue as a Shock Absorber). |
| **Scalability & Reliability** | • Độ sẵn sàng (Availability): **≥ 99,5%** toàn thời gian (**≥ 99,9%** trong khung giờ ca thi đã lên lịch). • **RPO ≤ 15 phút** cho dữ liệu hệ thống chung; **RPO ≤ 20 giây** trên máy chủ cho phiên đang thi (**≤ 5 giây** ở phút cuối và **RPO ≈ 0** trên thiết bị nhờ lớp lưu nháp IndexedDB). • **RTO ≤ 30 phút** khi khôi phục sự cố dịch vụ. | • Container Autoscaling (Stateless) kết hợp **Scheduled Pre-warming** trước ca thi 15 phút. • Lưu nháp 2 lớp (IndexedDB \+ Redis) và cơ chế **Server-side Auto-Finalize** khi hết giờ. • **Circuit Breaker (Chế độ Cứu hộ Ca thi)** tự động ngắt tính năng phụ khi quá tải. |
| **Security** | • TLS 1.3 trên đường truyền; mã hóa AES-256 ở tầng lưu trữ; 100% API đi qua lớp kiểm tra RBAC. • **Bảo vệ luồng phát Video HLS trên Cloudflare R2 (\[HIỆU ĐÍNH v1.3\]):** sử dụng **Cloudflare Worker HLS Auth Proxy** kết hợp [Cloudflare WAF Token Authentication (HMAC-SHA256)](https://developers.cloudflare.com/waf/custom-rules/use-cases/configure-token-authentication/) kiểm tra Signed Session Cookie tại Edge (khắc phục lỗi ghép nhầm AWS CloudFront Signed Cookies vào Cloudflare R2 ở v1.2), kết hợp **Dynamic Watermark** (Email/Mã học viên) di chuyển nhẹ trên khung hình. • Chống gian lận & AI Safety: khóa AI theo phiên thi ở Backend; tách biệt dữ liệu đầu vào bằng XML Delimiters để chống Prompt Injection. | • Giao dịch kích hoạt mã Atomic/Idempotent. • Cho phép đăng nhập tối đa 3 thiết bị nhưng khóa cứng **1 luồng phát video hoạt động (1 Active Stream)** trên Redis và bật **Single-Device Exam Lock** khi đang thi. • Không trả đáp án đúng về trình duyệt trước khi đóng ca thi hoặc khi học viên còn lượt làm lại Post-test. |
| **Interoperability** | • 100% RESTful API tuân thủ đặc tả **OpenAPI 3.0**. • Hỗ trợ nhập/xuất Ngân hàng câu hỏi và Bảng điểm qua định dạng chuẩn **CSV / JSON** (lược bỏ SCORM/LTI trong MVP); chứng nhận số tuân thủ chuẩn [**1EdTech Open Badges 3.0**](https://www.imsglobal.org/spec/ob/v3p0/cert). | • API Gateway / OpenAPI Swagger tự động sinh tài liệu kiểm thử; chứng nhận số tích hợp siêu dữ liệu JSON-LD có chữ ký mật mã. |
| **Usability & Accessibility** | • Giao diện Responsive Web (Desktop/Tablet/Mobile); hỗ trợ song ngữ Vi/En. • Đạt tiêu chuẩn tiếp cận [**W3C WCAG 2.1 Level AA**](https://www.w3.org/TR/WCAG21/) **cơ bản**: trình phát video và giao diện làm bài trắc nghiệm điều khiển được 100% bằng bàn phím (Tab, Space, phím mũi tên/số), có nhãn ARIA và phụ đề .vtt đồng bộ. | • File phụ đề .vtt do Admin tải lên đóng vai trò kép: vừa đáp ứng chuẩn tiếp cận WCAG, vừa làm nguồn dữ liệu đầu vào kèm mốc thời gian cho RAG. |

## **4.3. Tuân thủ Pháp lý & Bảo vệ Dữ liệu Cá nhân \[QĐ\]**

1. **Khung pháp lý áp dụng:** Tuân thủ **Luật Bảo vệ dữ liệu cá nhân số 91/2025/QH15** và **Nghị định 356/2025/NĐ-CP** (có hiệu lực từ ngày 01/01/2026, thay thế Nghị định 13/2023/NĐ-CP).  
2. **Đồng ý rõ ràng (Explicit Consent):** Học viên xác nhận đồng ý rõ ràng ngay tại bước tự đăng ký tài khoản / kích hoạt mã về mục đích thu thập và xử lý dữ liệu tiến độ học tập, nhật ký làm bài thi và dữ liệu hành vi tương tác video (Heartbeat).  
3. **Ẩn danh hóa trước khi gọi dịch vụ AI bên thứ ba (PII Stripping):** Hệ thống tuyệt đối không gửi thông tin định danh cá nhân (Họ tên, Số điện thoại, Email, Địa chỉ IP) sang API của nhà cung cấp LLM bên ngoài; mọi truy vấn RAG chỉ gắn mã phiên ẩn danh (ephemeral\_session\_hash).  
4. **Quyền của chủ thể dữ liệu & Chính sách Tài khoản Ngủ đông (Dormant Data Retention):** Học viên có quyền trích xuất lịch sử học tập/điểm số của mình và yêu cầu khóa/xóa tài khoản. Dù học viên được cấp quyền xem học liệu tĩnh vĩnh viễn theo vòng đời khóa học, nếu tài khoản **không phát sinh đăng nhập liên tục trong 24 tháng**, hệ thống tự động xóa/ẩn danh hóa toàn bộ nhật ký hành vi chi tiết (Heartbeat logs) để tuân thủ nguyên tắc giới hạn lưu trữ dữ liệu cá nhân của Nghị định 356/2025/NĐ-CP.

# **5\. ĐÁNH ĐỔI TRONG THIẾT KẾ MVP (ARCHITECTURE & PRODUCT TRADE-OFFS)**

Bám sát nguyên tắc của học phần: *"Build less — but design, implement, measure and defend it well"* và khung kiến trúc ứng dụng P2 (*Modular Monolith \+ Data Ownership \+ Sync/Async Queue*):

| Bài toán thiết kế | Không làm trong MVP (Out-of-Scope) | Giải pháp lựa chọn & Lý do bảo vệ (Rationale) |
| :---- | :---- | :---- |
| **1\. Phạm vi tính năng & Kiến trúc Ứng dụng** | Microservices phân tán phức tạp, Live Chat 1-1, diễn đàn đa cấp, Gamification phức tạp, cổng thanh toán trực tuyến | Chọn kiến trúc **Stateless Modular Monolith kết hợp Async Background Workers**: hoàn thiện 1 luồng nghiệp vụ khép kín (End-to-End Vertical Slice) từ tự đăng ký → kích hoạt mã → học video HLS → hỏi AI Socratic theo mốc giây → thi đồng loạt chịu tải cao → chấm tự luận theo Rubric mà không tốn chi phí vận hành hệ phân tán. |
| **2\. Chuẩn đóng gói nội dung & Tách Metadata / Media** | Chuẩn SCORM / xAPI / LTI phức tạp; đẩy luồng byte video đi xuyên qua máy chủ API | Sử dụng mô hình Course → Module → Lesson gắn LO trực tiếp trên CSDL quan hệ (Metadata → Database), còn tệp thực tế (Actual File → Object Storage) tải trực tiếp qua Presigned URL và phát qua Edge CDN. |
| **3\. Chống gian lận thi cử** | Giám sát qua Webcam AI (Proctoring), ép cài phần mềm khóa trình duyệt (Safe Exam Browser) | Dùng **Đồng hồ máy chủ \+ Hard Cutoff \+ Đảo câu hỏi/đáp án \+ Khóa AI Tutor ở Backend \+ Single-Device Exam Lock \+ Dynamic Watermark \+ \[STRETCH\] AI Viva Voce**. Đảm bảo tính công bằng học thuật mà không xâm phạm quyền riêng tư hình ảnh hay gây lỗi tương thích thiết bị. |
| **4\. Phân quyền biên soạn & Ngân hàng đề** | Instructor tạo/sửa khóa học, LO, đề thi, nạp học liệu hoặc duyệt câu hỏi AI | **Chỉ Admin học vụ** nạp giáo trình, cấu hình tỷ trọng điểm, quản lý Ngân hàng câu hỏi (bao gồm **độc quyền duyệt câu hỏi do AI sinh ra**) và cấu hình Rubric \[QĐ\]; **Instructor** tách biệt hoàn toàn khỏi quyền sửa dữ liệu gốc, tập trung 100% vào chấm bài tự luận từ Hàng đợi chung và giải đáp Q\&A (kèm nút Flag Issue báo Admin khi cần). |
| **5\. Quyền xem lại vĩnh viễn vs. Chi phí Cloud/AI** | Cấp quyền gọi AI Tutor/chấm bài vĩnh viễn hoặc mở băng thông không kiểm soát cho tài khoản cũ | Tách quyền thông minh theo mô hình [edX Verified Archived Access](https://support.edx.org/hc/en-us/articles/360013318054-Does-upgrading-to-the-verified-track-extend-my-access-to-the-course) \[QĐ\]: Quyền tốn chi phí biến đổi (LLM/Instructor) khóa ngay khi hết Access Period; quyền xem tĩnh mở **vĩnh viễn theo vòng đời khóa học** nhưng gắn **giới hạn 1 phiên/1 luồng phát \+ 15 giờ video/tháng \+ Dormant Policy 24 tháng** và tận dụng [Cloudflare R2](https://www.cloudflare.com/products/r2/) (**0 USD Egress**). |
| **6\. Hạ tầng Vector Database & Tách Đọc/Ghi (CQRS)** | Tự dựng cụm Vector DB riêng (Milvus / Qdrant / Pinecone) hoặc ghi nháp thi trực tiếp vào bảng SQL chính | • Dùng trực tiếp tiện ích mở rộng **pgvector ngay trên PostgreSQL** chính của hệ thống \[QĐ\] (truy vấn HNSW \< 15ms, hỗ trợ JOIN thẳng với bảng Lesson đã mở khóa và tốn **0 USD chi phí phát sinh**). • Tách Read/Write Model trong ca thi: Đọc đề 100% từ **Redis Cache**, ghi nháp Delta Sync vào **Redis**, và dùng **Message Queue làm bộ giảm chấn (Shock Absorber)** khi nộp bài. |

# **6\. YÊU CẦU CHỨC NĂNG CỐT LÕI (FUNCTIONAL REQUIREMENTS — P1 & P2)**

| Mã FR | Nhóm chức năng | Đặc tả chi tiết (Bản chốt \[QĐ\] & \[HIỆU ĐÍNH v1.3\]) |
| :---- | :---- | :---- |
| **FR-01** | Quản lý Khóa học & Học liệu (Course & Media) | • **Chỉ Admin** tạo/sửa cấu trúc Course → Module → Lesson, gắn LO và mức nhận thức Bloom; quản lý Ngân hàng câu hỏi, cấu hình Rubric, cấu hình tỷ trọng điểm xét chứng nhận (weight\_% \= 100%) và max\_attempts; kiểm tra Validation Rule (≥ 1 học liệu, ≥ 3 câu Mini-quiz, ≥ 1 câu thi chính thức cho mỗi LO) khi Publish. • Admin tải video .mp4 (≤ 2 GB), phụ đề .vtt (≤ 5 MB) và tài liệu PDF/DOCX (≤ 20 MB) qua Presigned URL; **Async Media Worker** đóng gói sang HLS (.m3u8 \+ .ts) và bóc tách .vtt phục vụ tìm kiếm/RAG. |
| **FR-02** | Đăng ký, Ghi danh bằng Mã kích hoạt (Onboarding & Enrollment) | • **Học viên tự đăng ký tài khoản (Self-registration \+ xác thực Email \+ Explicit Consent)**; tài khoản Instructor và Admin do Admin cấp phát nội bộ \[QĐ\]. • Admin tạo lô Mã kích hoạt (CSV/QR) theo khóa, thu hồi, cấp lại hoặc phát hành Mã gia hạn. • Học viên nhập mã 1 lần để mở quyền truy cập toàn khóa (xử lý Atomic & Idempotent) trong Access Period (mặc định 90/180 ngày). • Hệ thống tự động áp dụng **Chế độ Lưu trữ Xem tĩnh (Archived Read-Only Mode)** khi hết Access Period: khóa quyền tương tác có chi phí, giữ quyền xem tĩnh vĩnh viễn theo vòng đời khóa học (kèm giới hạn 1 phiên/1 luồng phát, 15 giờ video/tháng và chính sách ngủ đông 24 tháng). |
| **FR-03** | Học tập, Kiểm soát Luồng phát & Lộ trình Thích ứng (Learning & Fast-track) | • Phát video HLS qua Cloudflare CDN/Workers bảo vệ bằng **HMAC Signed Session Cookies** và **Dynamic Watermark**. • Cho phép đăng nhập tối đa **03 thiết bị cá nhân**, nhưng khóa cứng **01 luồng phát video hoạt động (1 Active Media Stream)** qua Heartbeat (mỗi 30 giây khi đang phát video) \[QĐ\]. • Mở khóa bài học tuần tự khi đạt **ĐỒNG THỜI (AND) xem ≥ 80% thời lượng video \+ đọc tài liệu VÀ đạt ≥ 70% Mini-quiz** \[QĐ\]. • Hỗ trợ **Fast-track qua Pre-test** (đạt ≥ 85% theo LO được miễn điều kiện xem 80% video của LO đó, nhưng vẫn phải làm Post-test/Thi chính thức để xét chứng nhận). |
| **FR-04** | Kiểm tra, Học bù & Kỳ thi Đồng loạt (Mass Exam & Concurrency) | • Hỗ trợ Pre-test (max\_attempts \= 1), Post-test (ẩn đáp án đúng khi còn lượt làm lại; nếu trượt hết max\_attempts thì **tự động mở thêm 01 lượt thi lại sau khi học viên xem lại đủ 100% video của Module và chờ Cooldown 24 giờ** \[QĐ\]), Mini-quiz (Unlimited, chỉ hiện gợi ý Socratic khi \< 70% và trộn câu hỏi từ Pool) và Kỳ thi đồng loạt (max\_attempts \= 1, ≥ 500 CCU). • Kiểm soát thời gian bằng đồng hồ máy chủ với **Hard Cutoff (T\_end)** \+ **End-of-Exam Aggressive Sync (5s/lần ở 60s cuối)** \+ **Server Grace Window (T\_end \+ 30s)** \+ **Server-side Auto-Finalize** từ bản nháp Redis nếu học viên mất mạng. • Lưu nháp 2 lớp (IndexedDB \+ Redis Delta Sync); nộp bài qua Client Jitter (0–10s) \+ Message Queue; hỗ trợ **Pre-warming** và **Chế độ Cứu hộ Ca thi (Circuit Breaker)** \[ĐỘT PHÁ \- MVP\]. |
| **FR-05** | Bài tự luận & Hàng đợi Chấm (Shared Grading Queue) | • Học viên nộp bài tự luận trực tiếp hoặc tải file PDF/DOCX text-searchable (**dung lượng ≤ 20 MB/tệp**, xác thực trích xuất **≥ 50 từ / 300 ký tự văn bản hợp lệ**) \[QĐ\]. • Hàng đợi chấm bài chung hỗ trợ **Claim/Lock (TTL 30 phút \+ Auto-extend Lease \+ Optimistic Lock Token)** và **Chấm ẩn danh (Blind Grading)** theo Rubric từng tiêu chí. • Hỗ trợ Resubmit (trước khi bị Claim), Phúc khảo 1 lần/bài trong 7 ngày bởi Instructor thứ 2 độc lập (điểm phúc khảo là điểm quyết định cuối cùng, kèm ghi log và cảnh báo cho Admin khi lệch \> 15%), \[STRETCH\] **AI Pre-Grading** và \[STRETCH\] **AI Viva Voce**. |
| **FR-06** | Thảo luận Q\&A & Thông báo (Q\&A & Async Notification) | • Q\&A Thread gắn theo từng bài học và mốc giây video; hỗ trợ \[STRETCH\] gợi ý câu hỏi tương tự (Semantic FAQ Deflection) và tự động nạp câu trả lời chuẩn vào RAG. • Gửi thông báo In-app và Email bất đồng bộ qua Message Queue (kèm cơ chế Retry 3 lần \+ Dead-Letter Queue khi dịch vụ gửi mail gặp lỗi). |
| **FR-07** | Chứng nhận Mở & Phân tích Học tập (Analytics & Open Badges) | • Tự động xét điều kiện hoàn thành (100% Module bắt buộc \+ Điểm trung bình trọng số kiểm tra chính thức **≥ 70%** theo tỷ trọng weight\_% do Admin cấu hình cho khóa học), thu thập khảo sát CSAT/NPS và xuất **Digital Certificate chuẩn [1EdTech Open Badges 3.0](https://www.imsglobal.org/spec/ob/v3p0/cert)**. • Cung cấp Dashboard cho Instructor/Admin (Completion, Drop-off, Learning Gain, chi phí Token AI, \[STRETCH\] Rewind Heatmap & Misconception Radar) và **Bản đồ thành thạo LO** cho học viên (\[STRETCH\] FSRS Spaced Repetition & Adaptive Remediation Playlist). |
| **FR-08** | Trợ lý AI Socratic & Kiểm định (AI Tutor & Evaluation) | • **Timestamp-Aware Socratic AI Tutor** \[ĐỘT PHÁ \- MVP\]: chỉ trả lời trong phạm vi Lesson đã mở khóa, tự động đính kèm ngữ cảnh mốc giây video đang dừng, trả về trích dẫn trang/mốc giây có link nhảy trực tiếp; tự động khóa ở tầng Backend khi học viên đang thi/Pre-test/Post-test; giới hạn 20 lượt/ngày. • **AI Giải thích lỗi sai** \[ĐỘT PHÁ \- MVP\] lưu Cache theo cặp (question\_id, selected\_option\_id) (mở cho cả tài khoản xem tĩnh đối với câu đã có trong Cache). • **AI Quiz Generator** do **chỉ Admin độc quyền kiểm duyệt (Approve / Edit / Reject)** trước khi đưa vào Ngân hàng câu hỏi \[QĐ\], kèm bộ **Evaluation Pipeline** đo độ chính xác, chống Prompt Injection và thống kê chi phí token. |

# **7\. PHÂN TÍCH TẢI & ƯỚC TÍNH CHI PHÍ (WORKLOAD & FINOPS ANALYSIS) \[QĐ\]**

*Bảng tính toán định lượng đã chốt cho quy mô vận hành **1.500 học viên hoạt động (Active Learners)** và ca thi đỉnh tải **500 CCU**, dùng làm cơ sở thiết kế kiến trúc Cloud và bảo vệ đồ án (Architecture Defense).*

## **7.1. Ước tính Tải Hệ thống — Phân tách Luồng Đọc (Read-Heavy) và Luồng Ghi (Write-Heavy Burst)**

| Hạng mục tải | Đặc tính Workload (Theo Khung P2) | Công thức & Giả định định lượng (Đã chốt \[QĐ\]) | Con số mục tiêu thiết kế |
| :---- | :---- | :---- | :---- |
| **1\. Lưu trữ Học liệu (Object Storage)** | Lưu trữ Tệp Thực tế (Actual File → Object Storage) | • 5 khóa học × 40 Lesson/khóa \= **200 Lesson**. • Mỗi Lesson có 1 video 60 phút ở 2 mức chất lượng HLS (480p \+ 720p ≈ 1,0 GB/Lesson; video gốc .mp4 ≤ 2 GB) \+ PDF/tài liệu/phụ đề .vtt (20 MB). • Tổng dung lượng học liệu tĩnh \+ bài nộp PDF/DOCX (≤ 20 MB/bài) của học viên: 200 × 1,02 GB \+ 20 GB ≈ 225 GB. | **\~250 GB** lưu trữ Object Storage |
| **2\. Băng thông Video hàng tháng (Media Egress)** | **Cao điểm Xem Video (Read-Heavy Media Stream)** | • 1.500 học viên đang học × trung bình 8 giờ xem video/tháng × 0,65 GB/giờ \= **7.800 GB/tháng**, cộng \~500 học viên cũ xem lại tĩnh (bị giới hạn tối đa 15 giờ/tháng, thực tế trung bình \~2 giờ/người/tháng ≈ 650 GB) → Tổng cộng **\~8,5 TB/tháng**. • Lưu lượng đỉnh giờ cao điểm buổi tối (20:00–22:00): \~300 học viên xem đồng thời ≈ **450 Mbps** (do Cloudflare Edge CDN gánh 95%+, 0 Mbps đi qua API Server). | **\~8,5 TB Egress/tháng** qua Edge CDN |
| **3\. Tải Ghi Tiến độ Học tập (Progress Heartbeat)** | Tải Ghi Nền (Buffered Write) | • Giờ cao điểm thông thường có 300 học viên xem video đồng thời, gửi Heartbeat mỗi 30 giây: 300 ÷ 30 \= 10 RPS (ghi vào Redis để giữ khóa 1 Active Stream, rồi gom lô flush xuống PostgreSQL mỗi 2 phút). | **10–15 RPS** (tải nền rất nhẹ) |
| **4\. Tải Duy trì trong Ca thi Đồng loạt (Sustained Exam Load)** | **Cao điểm Ghi Duy trì (Sustained Write)** | • **500 CCU** làm bài cùng lúc; chu kỳ đồng bộ nháp mỗi 20 giây: tối đa 500 ÷ 20 \= 25 RPS (nhờ **Dirty-state Delta Sync** chỉ gửi khi đổi đáp án, còn \~15–20 RPS). • Trong 60 giây cuối (Aggressive Sync 5s/lần cho \~150 thí sinh còn đổi đáp án): 150 ÷ 5 \= 30 RPS vào Redis. | **30 – 60 RPS** duy trì suốt ca thi |
| **5\. Tải Đỉnh khi Mở đề & Nộp bài (Peak Exam Burst)** | **Đỉnh Đọc (T\_start) & Đỉnh Ghi (T\_end)** | • **Lúc mở đề (20:00:00 — Read Burst):** 500 học viên tải đề trong 5–10 giây đầu → **50–100 RPS** đọc 100% từ **Redis Cache** (đề đã được Pre-warm sẵn, 0 truy vấn nặng vào DB). • **Lúc hết giờ (21:00:00 — Write Burst):** \~350 học viên còn lại đến giây cuối cùng được phân tán đều qua **Client Jitter (0–10 giây)** → 350 ÷ 10 \= 35 RPS đẩy vào **Message Queue (Shock Absorber)**, cộng các request kiểm tra trạng thái ≈ **120–180 RPS** tại API Gateway. | Thiết kế chịu đỉnh **200 RPS** tại API Gateway / Queue |
| **6\. Lưu lượng Token AI hàng tháng (AI & RAG Workload)** | Kết hợp Truy xuất Vector & Cache Lỗi sai | • **AI Tutor:** Trung bình mỗi ngày có 30% học viên hoạt động (450 người) hỏi 5 câu/ngày (trong hạn mức 20 câu/ngày) → 450 × 5 × 30 ngày \= 67.500 lượt/tháng (\~1.500 input tokens và \~350 output tokens/lượt). • **AI Giải thích lỗi sai:** Nhờ nạp sẵn lời giải thích phương án nhiễu vào **Cache theo (question\_id, option\_id)** (Cache Hit ≥ 90%), tốn \< 3.000 lượt gọi LLM mới/tháng; học viên xem lại tĩnh dùng 100% từ Cache (**0 token mới**). | **\~110 triệu Input Tokens** & **\~25 triệu Output Tokens / tháng** |

## **7.2. Ước tính Chi phí Cloud & AI Hàng tháng (FinOps Estimation)**

Nhờ chốt kiến trúc kết hợp [Cloudflare R2](https://developers.cloudflare.com/r2/pricing/) (**0 USD phí băng thông Egress** — giải bài toán "xem lại tĩnh vĩnh viễn" mà không sợ nổ chi phí băng thông), pgvector tích hợp sẵn trên PostgreSQL, và dòng mô hình LLM Gemini 2.5 Flash / GPT-4o-mini:

| Thành phần hạ tầng | Môi trường Demo / Load Test Đồ án (SV vận hành) | Môi trường Production thực tế (1.500 HV active \+ HV cũ xem lại tĩnh, 8,5 TB video/tháng) |
| :---- | :---- | :---- |
| **1\. Object Storage, CDN & Edge Auth Worker (Video HLS \+ Tài liệu)** | [Cloudflare R2](https://www.cloudflare.com/products/r2/) Free Tier (\< 10 GB lưu trữ, **0 USD Egress**) \+ Cloudflare Worker Free Tier (100.000 req/ngày xác thực HMAC Cookie cho .m3u8/.ts): **\$0 / tháng** | 250 GB trên Cloudflare R2 (\$0.015/GB) \+ 8,5 TB Egress (**\$0 phí băng thông ra**) \+ Cloudflare Workers Paid/WAF (\$5/tháng): **\~\$9 – \$12 / tháng** *(tiết kiệm \> \$700/tháng so với S3/CloudFront Egress chuẩn)* |
| **2\. Compute (Stateless API Server \+ Async Workers)** | 2–3 Container instances (chạy CI/CD \+ bật tăng scale khi chạy kịch bản Load Test 500 CCU trong vài giờ): **\~\$12 – \$20 / tháng** | Cụm Container Autoscaling (2 node nền 2 vCPU/4GB RAM \+ tự động scale lên 4–6 node trong khung giờ thi buổi tối): **\~\$45 – \$70 / tháng** |
| **3\. Database (PostgreSQL \+ pgvector) & Redis \+ Queue** | Managed/Containerized PostgreSQL (kèm pgvector) \+ Redis cơ bản: **\~\$10 – \$15 / tháng** | Managed PostgreSQL (High Availability, Daily Backup, tích hợp pgvector) \+ Redis Cache: **\~\$35 – \$55 / tháng** |
| **4\. Dịch vụ AI (LLM API \+ Embedding)** | Phục vụ kiểm thử Evaluation Dataset & Demo (\< 5 triệu tokens): **\~\$2 – \$5 / tháng** | \~110M Input \+ 25M Output tokens trên Gemini 2.5 Flash / GPT-4o-mini (có Mistake Cache ≥ 90%): **\~\$25 – \$35 / tháng** |
| **5\. Giám sát (Observability) & Email Notification** | Grafana Cloud / Prometheus Free Tier \+ Email Sandbox: **\$0 – \$5 / tháng** | Log retention 30 ngày \+ Dịch vụ Email transactional (SES/Resend): **\~\$5 – \$10 / tháng** |
| **TỔNG CHI PHÍ DỰ KIẾN** | **\~\$24 – \$45 / tháng** *(Nằm gọn trong ngân sách 30–60 USD của nhóm)* | **\~\$119 – \$182 / tháng** *(Chỉ \~0,08 – 0,12 USD / học viên đang học / tháng)* |

# **8\. KIẾN TRÚC HỆ THỐNG & KỊCH BẢN CHỊU LỖI (APPLICATION, DATA & FAILURE HANDLING — P1 & P2)**

## **8.1. Sơ đồ Kiến trúc Tổng thể (Cloud, Application, Data & Integration Architecture)**

```
[ Học viên / Giảng viên / Admin (Web Browser + IndexedDB Offline Exam Store) ]
          │                                       │
          │ (1) Phát Video HLS (.m3u8/.ts)        │ (2) REST API / SSE / Auth / Exam / AI (HTTPS TLS 1.3)
          │     & Direct Upload (Presigned URL)   │
          ▼                                       ▼
┌───────────────────────────┐           ┌───────────────────────────────────────────────┐
│ Cloudflare CDN + Edge Auth│           │  API Gateway / Reverse Proxy + Load Balancer  │
│ (Worker/WAF kiểm tra HMAC │           │  (Rate Limiting, JWT Auth, Circuit Breaker)   │
│  Signed Cookie & Cache)   │           └───────────────────────┬───────────────────────┘
└─────────────┬─────────────┘                                   │
              │ (Cache Miss - $0 Egress via R2 Binding)         ▼
              ▼                                 ┌───────────────────────────────────────┐
┌───────────────────────────┐                   │ Stateless Modular Monolith Containers │
│  Zero-Egress Object Store │◄─(Presigned Link)─┤ • M01: Identity, RBAC & Stream Lock   │
│  (Cloudflare R2 Storage)  │                   │ • M02: Curriculum, LO & Question Bank │
│  • Raw MP4 (≤2GB) + .vtt  │                   │ • M03: Media Control  • M04: Enroll   │
│  • HLS Segments & PDF/DOCX│                   │ • M05: Progress (AND) • M06: MassExam │
└─────────────┬─────────────┘                   │ • M07: Blind Grading  • M08: AI RAG   │
              │                                 │ • M09: Q&A Thread     • M10: Analytics│
              │                                 └───────┬───────────────────────┬───────┘
              ▼ (Async Media Upload Event)              │                       │
┌───────────────────────────────────────────────────────┴───┐   ┌───────────────▼───────┐
│        Message Queue / Event Broker (Shock Absorber)      │   │  Redis In-Memory      │
│  • exam-submission-queue (Ưu tiên cao nhất - High Prio)   │   │  • Exam Draft State   │
│  • media-transcoding- & email-notification-queue (+ DLQ)  │   │  • Pre-warmed Quizzes │
│  • ai-tasks-queue (Quiz Gen / Stretch AI Pre-grading)     │   │  • 1 ActiveStream Lock│
└─────────────────────────────┬─────────────────────────────┘   │  • Mistake Expl. Cache│
                              │                                 └───────────┬───────────┘
                              ▼                                             │
              ┌───────────────────────────────┐                             ▼
              │  Async Background Workers     │             ┌───────────────────────────┐
              │  • W01: Exam Grader/Finalizer │────────────►│  Primary Transactional DB │
              │  • W02: Media HLS & Chunker   │             │  (PostgreSQL + pgvector)  │
              │  • W03: Async AI Tasks Worker │             │  • Users, Courses, Codes  │
              │  • W04: Email & Dormant Worker│             │  • Grades, Rubrics, Q&A   │
              └───────────────┬───────────────┘             │  • Vector Embeddings (RAG)│
                              │                             └───────────────────────────┘
                              ▼ (PII-Stripped HTTPS + Timeout 10s + Circuit Breaker)
              ┌───────────────────────────────┐
              │  External LLM / Embedding API │
              │  (Gemini 2.5 Flash/GPT-4o-mini│
              └───────────────────────────────┘

```

## **8.2. Thiết kế Xử lý Sự cố Phụ thuộc (Failure Scenarios — Chuẩn bị cho P2 & P4)**

| Kịch bản lỗi (Failure Scenario) | Tác động nếu không xử lý | Cơ chế tự phục hồi & Giảm nhẹ (Mitigation & Fallback) |
| :---- | :---- | :---- |
| **1\. Học viên rớt mạng giữa chừng hoặc đúng giây cuối ca thi** | Mất toàn bộ đáp án chưa lưu hoặc không bấm nộp được khi hết giờ. | Trình duyệt giữ nguyên trạng thái làm bài trên IndexedDB; khi có mạng lại sẽ tự động đồng bộ bù. Trong 60 giây cuối trước T\_end, chu kỳ đồng bộ tự động rút ngắn xuống **5 giây/lần (Aggressive Sync)**. Nếu mất mạng hoàn toàn đến hết giờ (\> T\_end \+ 30s), **Server-side Auto-Finalize Worker (W01)** tự động chấm điểm dựa trên bản nháp mới nhất đã lưu trong Redis. |
| **2\. Dịch vụ LLM bên thứ ba bị lỗi (Timeout / HTTP 429 / 503\)** | Học viên bấm hỏi AI Tutor bị treo trang; luồng học bị gián đoạn. | Áp dụng **Timeout (10s) \+ Circuit Breaker**: • Với **AI Giải thích lỗi sai**: vẫn trả về bình thường cho ≥ 90% trường hợp đã có trong Cache. • Với **AI Tutor**: trả về thông báo thân thiện kèm danh sách **Top-3 đoạn Transcript .vtt liên quan nhất** lấy trực tiếp từ tìm kiếm pgvector/Full-text nội bộ (Retrieval-only Fallback) mà không cần LLM sinh văn bản. |
| **3\. Worker chấm điểm ca thi hoặc gửi Email bị sập đột ngột** | Mất yêu cầu nộp bài hoặc thất lạc email thông báo kết quả. | Các thông điệp nằm an toàn trên **Persistent Message Queue** (chỉ xóa khỏi hàng đợi khi Worker gửi tín hiệu ACK sau khi đã ghi DB thành công, kết hợp Idempotency Key chống chấm trùng). Với Email lỗi quá 3 lần (Exponential Backoff), đẩy vào **Dead-Letter Queue (DLQ)** để Admin kiểm tra và phát lại, đồng thời thông báo In-app vẫn hiển thị ngay cho học viên. |
| **4\. Đỉnh tải tăng đột biến gấp 5× (2.500 CCU mở đề cùng lúc)** | Quá tải kết nối cơ sở dữ liệu PostgreSQL, làm treo toàn hệ thống. | Đề thi được đọc 100% từ **Redis Cache (CQRS Read Model)**; trạng thái làm bài chỉ ghi vào Redis và gom lô (Batch write); **Chế độ Cứu hộ Ca thi (Exam Panic Mode)** tự động ngắt tạm thời các API nặng (AI Tutor, Analytics, Q\&A Search) để bảo vệ tuyệt đối luồng làm bài và nộp bài. |

# **PHỤ LỤC A — BẢNG TỔNG HỢP TOÀN BỘ QUYẾT ĐỊNH ĐÃ CHỐT (\[QĐ\] & \[HIỆU ĐÍNH v1.3\])**

| Mã | Hạng mục quyết định | Nội dung đã chốt chính thức trong BRD v1.3 (Đồng bộ 100% với SRS v1.1 & Kiến trúc P2) |
| :---- | :---- | :---- |
| **Q1** | **Tự đăng ký Tài khoản & Thời hạn học (Access Period)** | • **Học viên tự đăng ký tài khoản (Self-registration \+ xác thực Email \+ Explicit Consent)**; tài khoản Instructor và Admin do Admin cấp phát nội bộ (TBD-07). • Thời hạn học tính linh hoạt **N ngày kể từ thời điểm kích hoạt mã** (mặc định 90 hoặc 180 ngày do Admin cấu hình); hỗ trợ Mã gia hạn. |
| **Q2** | **Chế độ Lưu trữ Xem tĩnh (Archived Static Access)** | Chuẩn hóa theo mô hình [edX Verified Archived Access](https://support.edx.org/hc/en-us/articles/360013318054-Does-upgrading-to-the-verified-track-extend-my-access-to-the-course): Mở **vĩnh viễn theo vòng đời khóa học**, kèm **2 rào chắn bảo vệ thực tế**: (1) Giới hạn 1 phiên / 1 luồng phát & hạn mức Fair-Use 15 giờ video/tháng cho tài khoản đã hết Access Period; (2) Chính sách tài khoản ngủ đông (Dormant Policy) sau 24 tháng không đăng nhập để xóa log hành vi tuân thủ Nghị định 356/2025/NĐ-CP. |
| **Q3** | **Giới hạn Thiết bị & Khóa Cứng 1 Luồng Phát Video** | Trong Access Period, cho phép lưu đăng nhập tối đa **03 thiết bị cá nhân**, nhưng khóa cứng **01 luồng phát video hoạt động (1 Concurrent Active Media Stream)** tại một thời điểm qua Redis Heartbeat (TBD-02); kích hoạt **Single-Device Exam Lock** chặn thiết bị mới khi đang trong phiên thi đồng loạt / Post-test. |
| **Q4** | **Phân quyền Biên soạn & Duyệt Đề AI (Admin-only)** | **Chỉ Admin học vụ** tạo/sửa cấu trúc Course → Module → Lesson, gắn LO, nạp học liệu gốc, cấu hình Rubric, cấu hình tỷ trọng điểm xét tốt nghiệp và **độc quyền trực tiếp kiểm duyệt (Approve/Edit/Reject)** câu hỏi do AI sinh ra để đưa vào Ngân hàng câu hỏi (TBD-01). **Instructor** tách biệt hoàn toàn khỏi quyền sửa học liệu/ngân hàng đề, tập trung 100% vào chấm bài tự luận theo Rubric, trả lời Q\&A và bấm Flag Issue báo Admin. |
| **Q5** | **Học tuần tự (Toán tử VÀ), Fast-track & Chứng nhận** | • Học tuần tự yêu cầu hoàn thành **ĐỒNG THỜI (AND) xem ≥ 80% thời lượng video \+ đọc tài liệu VÀ đạt ≥ 70% Mini-quiz** (TBD-03). • Đạt ≥ 85% Pre-test theo LO giúp mở khóa nhanh bài học (miễn xem 80% video). • Xét cấp chứng nhận chuẩn [**1EdTech Open Badges 3.0**](https://www.imsglobal.org/spec/ob/v3p0/cert) bắt buộc hoàn thành 100% Module \+ điểm trung bình trọng số **Post-test / Thi đồng loạt / Tự luận ≥ 70%** theo tỷ trọng weight\_% \= 100% do Admin cấu hình cho từng khóa học (TBD-05; không lấy điểm Pre-test thay thế). |
| **Q6** | **Số lượt làm lại (Retake) & Học bù Mở lượt thi (Remediation)** | • Khóa cứng max\_attempts \= 1 cho Pre-test và Kỳ thi đồng loạt. • Với Post-test: ẩn đáp án đúng & ẩn giải thích AI khi còn lượt làm lại; nếu học viên trượt hết max\_attempts trong Access Period, hệ thống **tự động cấp thêm 01 lượt thi lại (Remediation Attempt) sau khi học viên xem lại đủ 100% video của Module đó và vượt qua thời gian chờ Cooldown 24 giờ** (TBD-06). • Với Mini-quiz (Unlimited): khi \< 70% chỉ hiển thị gợi ý Socratic (không lộ đáp án A/B/C/D) và trộn câu hỏi từ Pool (≥ 3 câu/LO). |
| **Q7** | **Kỳ thi đồng loạt (Cutoff, Aggressive Sync & Grace Window)** | Vào muộn tối đa **15 phút**; khóa chọn đáp án đúng T\_end (Hard Cutoff), tăng tốc đồng bộ nháp lên **5 giây/lần trong 60 giây cuối (End-of-Exam Aggressive Sync)**, mở **Cửa sổ ân hạn truyền tải 30 giây (T\_end \+ 30s)** nhận gói tin Client Jitter (0–10s), và tự động chấm từ bản nháp Redis (Server-side Auto-Finalize) nếu học viên rớt mạng. |
| **Q8** | **Hàng đợi chấm tự luận & Giới hạn Dung lượng Tệp** | • Khóa bài chấm **30 phút** kèm **Auto-extend Lease** khi đang gõ và lock\_token. • Chỉ nhận văn bản gõ trực tiếp hoặc tệp PDF/DOCX **dung lượng ≤ 20 MB/tệp** (TBD-04) trích xuất được **≥ 50 từ (300 ký tự)** văn bản hợp lệ (video bài giảng gốc .mp4 ≤ 2 GB/tệp, phụ đề .vtt ≤ 5 MB/tệp). |
| **Q9** | **Quy tắc Phúc khảo (Regrade)** | Tối đa 1 lần/bài trong 7 ngày kể từ khi có điểm, định tuyến cho Instructor thứ 2 độc lập; **điểm phúc khảo là điểm quyết định cuối cùng**, có ghi log độ lệch điểm và gửi cảnh báo cho Admin khi độ lệch \> 15%. |
| **Q10** | **Hạn mức AI Tutor & Cache lỗi sai** | Mặc định **20 lượt hỏi/học viên/ngày** (≤ 500 ký tự/câu) trong Access Period; lời giải thích lỗi sai được nạp sẵn vào Cache ngay từ khi Admin duyệt câu hỏi AI và mở cho cả học viên ở giai đoạn xem lại tĩnh (vì tốn 0 token mới). |
| **Q11** | **Phân tách Core MVP vs. Stretch Goal** | • **Core MVP:** *Timestamp-Aware Socratic AI Tutor*, *Cached Mistake Explanation*, *Exam Panic Circuit Breaker*. • **Stretch Goal (\[STRETCH\]):** *AI Viva Voce (2 câu hỏi vấn đáp xác thực bài tự luận)*, *AI Pre-Grading tự luận*, *Rewind Heatmap & Misconception Radar*, *Verified Q\&A → RAG Auto-Ingestion*, *FSRS Spaced Repetition & Adaptive Remediation Playlist*. |
| **Q12** | **Nguồn phụ đề Video (.vtt) & Bảo mật HLS trên Cloudflare** | Admin tải kèm file phụ đề .vtt/.srt chuẩn hóa sẵn của trung tâm khi upload video; Async Worker đóng gói HLS và bóc tách .vtt cho RAG; luồng phát HLS trên Cloudflare R2 được bảo vệ bằng **Cloudflare Worker / WAF HMAC Signed Session Cookies** tại Edge. |
| **Q13–16** | **Kiến trúc Ứng dụng, Dữ liệu & Hạ tầng Cloud (P1 & P2)** | Triển khai theo mô hình **Stateless Modular Monolith \+ Async Background Workers**; quy mô chuẩn **200 Lesson (\~250 GB)**, lưu trữ media trên [Cloudflare R2](https://developers.cloudflare.com/r2/pricing/) (**0 USD Egress**), Vector DB dùng **pgvector trên PostgreSQL**, Cache & Nháp thi dùng **Redis**, và LLM dùng **Gemini 2.5 Flash / GPT-4o-mini**. |

