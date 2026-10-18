# TÀI LIỆU ĐẶC TẢ CHI TIẾT 10 MODULE CƠ SỞ DỮ LIỆU
## HỆ THỐNG NỀN TẢNG GIÁO DỤC TRỰC TUYẾN (ONLINE EDUCATION PLATFORM - PROJECT 8)
*Áp dụng cho kiến trúc Modular Monolith trên PostgreSQL - Chuẩn hóa Baseline v2 & Schema Production*

---

# MỤC LỤC TỔNG QUAN

1. [Module 1: Quản lý Định danh & Xác thực (Identity & Access Management)](#module-1-quan-ly-dinh-danh--xac-thuc-identity--access-management)
2. [Module 2: Phân cấp Khóa học & Quản lý Media (Course Hierarchy & Content Management)](#module-2-phan-cap-khoa-hoc--quan-ly-media-course-hierarchy--content-management)
3. [Module 3: Ghi danh & Theo dõi Tiến độ Học tập (Enrollment & Progress Tracking)](#module-3-ghi-danh--theo-doi-tien-do-hoc-tap-enrollment--progress-tracking)
4. [Module 4: Động cơ Thi trắc nghiệm & Bản chụp Bất biến (Assessment - Quiz Engine)](#module-4-dong-co-thi-trac-nghiem--ban-chup-bat-bien-assessment---quiz-engine)
5. [Module 5: Bài tập Tự luận & Chấm điểm (Assessment - Assignment Engine)](#module-5-bai-tap-tu-luan--cham-diem-assessment---assignment-engine)
6. [Module 6: Thông báo Bất đồng bộ & Độ tin cậy Hệ thống (Async Notifications, Outbox & Audit)](#module-6-thong-bao-bat-dong-bo--do-tin-cay-he-thong-async-notifications-outbox--audit)
7. [Module 7: Nhật ký Thay đổi & Lịch sử Nội dung (Content Auditing & Versioning - Backlog)](#module-7-nhat-ky-thay-doi--lich-su-noi-dung-content-auditing--versioning---backlog)
8. [Module 8: Đánh giá Khóa học & Kênh Trao đổi (Course Reviews & Communication - Backlog)](#module-8-danh-gia-khoa-hoc--kenh-trao-doi-course-reviews--communication---backlog)
9. [Module 9: Mở rộng Đăng ký, Mã mời & Email Sự kiện (Advanced Onboarding & Invitations - Backlog)](#module-9-mo-rong-dang-ky-ma-moi--email-su-kien-advanced-onboarding--invitations---backlog)
10. [Module 10: Trợ lý Học tập AI Tutor RAG (AI Tutor Knowledge Assistant - Stretch P5)](#module-10-tro-ly-hoc-tap-ai-tutor-rag-ai-tutor-knowledge-assistant---stretch-p5)

---

## CÁC ENUMS DÙNG CHUNG TRONG TOÀN HỆ THỐNG

| Tên Enum | Giá trị hợp lệ | Ý nghĩa nghiệp vụ |
| :--- | :--- | :--- |
| `user_role` | `STUDENT`, `INSTRUCTOR`, `ADMIN` | Vai trò toàn cục (Global Role) của người dùng |
| `account_status` | `ACTIVE`, `DISABLED` | Trạng thái hoạt động của tài khoản |
| `auth_provider` | `LOCAL`, `GOOGLE` | Nguồn xác thực danh tính |
| `course_visibility` | `PUBLIC`, `PRIVATE` | Chế độ hiển thị khóa học trong tìm kiếm |
| `course_lifecycle_status` | `DRAFT`, `PUBLISHED`, `ARCHIVED`, `DISABLED` | Vòng đời phát hành và hiển thị khóa học |
| `learning_mode` | `FREE_NAVIGATION`, `SEQUENTIAL` | Chế độ điều hướng bài học (tự do hoặc tuần tự) |
| `course_instructor_role` | `OWNER`, `CO_INSTRUCTOR` | Quyền hạn của giảng viên trong khóa học cụ thể |
| `enrollment_status` | `ACTIVE`, `COMPLETED`, `WITHDRAWN`, `REMOVED` | Trạng thái ghi danh của học viên |
| `content_type` | `TEXT`, `LINK`, `VIDEO`, `PDF`, `SLIDE`, `DOCUMENT`, `FILE` | Định dạng tài nguyên học tập |
| `upload_status` | `PENDING`, `UPLOADED`, `AVAILABLE`, `FAILED` | Trạng thái xử lý tải tệp lên AWS S3 |
| `lesson_progress_status`| `NOT_STARTED`, `IN_PROGRESS`, `COMPLETED` | Tiến độ học tập từng bài học |
| `question_type` | `TRUE_FALSE`, `SINGLE_CHOICE`, `MULTIPLE_CHOICE`, `KEYWORD_FILL` | Dạng câu hỏi trắc nghiệm |
| `quiz_grading_policy` | `HIGHEST`, `LATEST`, `AVERAGE` | Quy tắc chọn điểm khi làm bài nhiều lần |
| `quiz_attempt_status` | `IN_PROGRESS`, `SUBMITTED`, `TIMED_OUT`, `GRADED` | Trạng thái phiên làm bài thi |
| `assignment_status` | `SUBMITTED`, `PENDING_REGRADE`, `GRADED` | Trạng thái nộp và chấm bài tập |
| `notification_delivery_status` | `PENDING`, `SENT`, `FAILED` | Trạng thái gửi email thông báo |
| `outbox_status` | `PENDING`, `PROCESSED`, `FAILED` | Trạng thái hàng đợi Outbox trung chuyển |
| `instructor_application_status` | `PENDING`, `APPROVED`, `REJECTED` | Trạng thái xét duyệt hồ sơ giảng viên |
| `enrollment_request_status` | `PENDING`, `APPROVED`, `REJECTED`, `CANCELLED` | Trạng thái yêu cầu vào khóa học riêng tư |
| `invitation_status` | `PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED` | Trạng thái lời mời tham gia khóa học |

---

# MODULE 1: QUẢN LÝ ĐỊNH DANH & XÁC THỰC (IDENTITY & ACCESS MANAGEMENT)

### 1. Mục đích & Vai trò trong hệ thống
* **Mục đích:** Quản lý toàn bộ vòng đời tài khoản người dùng, cơ chế xác thực đa phương thức (Local JWT / Google OAuth2), phân quyền RBAC cấp cao, và thu hồi phiên làm việc tức thì.
* **Vai trò:** Cổng kiểm soát bảo mật đầu vào (Gateway Guard) của toàn bộ nền tảng. Chịu trách nhiệm cấp phát các Access Token ngắn hạn và quản lý Refresh Token dài hạn, bảo vệ thông tin mật khẩu bằng các thuật toán băm một chiều an toàn (`bcrypt` / `argon2id`).

### 2. Vai trò & Quyền hạn theo Role (RBAC)
* **Student:**
  * Tự đăng ký tài khoản cục bộ bằng Email và mật khẩu.
  * Cập nhật thông tin cá nhân (họ tên, ảnh đại diện, số điện thoại, tiểu sử) trong `user_profiles`.
  * Đăng nhập, nhận Access Token để thao tác hệ thống; làm mới token qua Refresh Token; đăng xuất (hủy token).
* **Instructor:**
  * Nhận tài khoản do Admin khởi tạo (hoặc duyệt đơn) kèm mật khẩu tạm thời.
  * Lần đầu đăng nhập bắt buộc phải đổi mật khẩu (`must_change_password = true`) mới được kích hoạt đầy đủ quyền.
  * Quản lý hồ sơ chuyên môn, đơn vị công tác và tiểu sử giảng viên.
* **Admin:**
  * Tạo tài khoản trực tiếp cho Instructor.
  * Khóa (`DISABLED`) hoặc mở khóa (`ACTIVE`) tài khoản người dùng vi phạm.
  * Tuyệt đối không thay đổi vai trò (`role`) của người khác và không can thiệp mật khẩu trực tiếp.

### 3. Danh sách các bảng liên quan
* `users`
* `user_profiles`
* `auth_identities`
* `refresh_tokens`

### 4. Chi tiết cấu trúc các bảng

#### Bảng `users` (Thông tin tài khoản cốt lõi)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Định danh duy nhất toàn cầu của người dùng |
| `email` | `varchar(255)` | NOT NULL, UNIQUE | Địa chỉ email đăng nhập duy nhất (chuẩn hóa chữ thường) |
| `role` | `user_role` | NOT NULL, Default: `'STUDENT'` | Vai trò toàn cục của tài khoản (`STUDENT`, `INSTRUCTOR`, `ADMIN`) |
| `account_status` | `account_status` | NOT NULL, Default: `'ACTIVE'` | Trạng thái hoạt động (`ACTIVE` được phép đăng nhập, `DISABLED` bị cấm) |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo tài khoản (chuẩn UTC) |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm cập nhật trạng thái tài khoản gần nhất |

#### Bảng `user_profiles` (Hồ sơ cá nhân mở rộng)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `user_id` | `uuid` | PK, FK $\rightarrow$ `users(id)` ON DELETE CASCADE | Khóa ngoại tham chiếu 1-1 tới bảng tài khoản người dùng |
| `full_name` | `varchar(255)` | NOT NULL | Họ và tên đầy đủ hiển thị trên hệ thống |
| `avatar_object_key` | `varchar(500)` | Nullable | Khóa tham chiếu ảnh đại diện lưu trên AWS S3 |
| `phone` | `varchar(50)` | Nullable | Số điện thoại liên lạc của người dùng |
| `bio` | `text` | Nullable | Tiểu sử, mô tả ngắn về bản thân hoặc kinh nghiệm giảng dạy |
| `organization` | `varchar(255)` | Nullable | Đơn vị học tập hoặc cơ quan công tác |
| `date_of_birth` | `date` | Nullable | Ngày tháng năm sinh |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm chỉnh sửa hồ sơ gần nhất |

#### Bảng `auth_identities` (Thông tin phương thức xác thực)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của bản ghi định danh xác thực |
| `user_id` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Liên kết tới tài khoản sở hữu phương thức xác thực |
| `provider` | `auth_provider` | NOT NULL, Default: `'LOCAL'` | Nhà cung cấp định danh (`LOCAL` mật khẩu nội bộ, `GOOGLE` OAuth2) |
| `provider_user_id` | `varchar(255)` | Nullable | Định danh duy nhất phía provider ngoại (ví dụ: Google Sub ID) |
| `password_hash` | `varchar(255)` | Nullable | Chuỗi mật khẩu đã băm một chiều (Argon2id/Bcrypt), NULL nếu là Google |
| `email_verified_at` | `timestamptz` | Nullable | Thời điểm email được xác thực qua mã OTP hoặc link kích hoạt |
| `must_change_password`| `boolean` | NOT NULL, Default: `false` | Cờ ép buộc đổi mật khẩu ở lần đầu đăng nhập (áp dụng cho Instructor) |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm khởi tạo phương thức xác thực |

#### Bảng `refresh_tokens` (Quản lý phiên làm việc & Thu hồi Token)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính định danh phiên làm việc |
| `user_id` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Tài khoản sở hữu phiên đăng nhập |
| `token_hash` | `varchar(255)` | NOT NULL, UNIQUE | Bản băm SHA-256 của Refresh Token cấp cho Client |
| `device_info` | `varchar(255)` | Nullable | Thông tin thiết bị/trình duyệt đăng nhập (phục vụ Audit & Bảo mật) |
| `expires_at` | `timestamptz` | NOT NULL | Thời điểm hết hạn của Refresh Token (thường 7-30 ngày) |
| `is_revoked` | `boolean` | NOT NULL, Default: `false` | Cờ thu hồi token (đặt thành `true` khi đăng xuất hoặc đổi mật khẩu) |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm cấp phát Refresh Token |

---

# MODULE 2: PHÂN CẤP KHÓA HỌC & QUẢN LÝ MEDIA (COURSE HIERARCHY & CONTENT MANAGEMENT)

### 1. Mục đích & Vai trò trong hệ thống
* **Mục đích:** Cung cấp cấu trúc phân cấp học tập chặt chẽ 4 tầng: `Course` $\rightarrow$ `Module` $\rightarrow$ `Lesson` $\rightarrow$ `Content`, hỗ trợ đa định dạng tài nguyên học tập (Video, PDF, Slide, Text) và tích hợp cơ chế phân phối qua CDN.
* **Vai trò:** Trọng tâm quản lý tri thức của hệ thống (Engineering Focus Media & CDN). Đảm bảo video dung lượng lớn được tải lên S3 trực tiếp qua Presigned URL và phân phối an toàn đến học viên qua CloudFront Signed URL ngắn hạn, không lưu tệp trực tiếp trong cơ sở dữ liệu.

### 2. Vai trò & Quyền hạn theo Role (RBAC & ABAC)
* **Student:**
  * Chỉ được truy cập nội dung bài học khi khóa học ở trạng thái `PUBLISHED` hoặc `ARCHIVED` và bản thân có bản ghi `enrollments.status = 'ACTIVE'`.
  * Nhận Signed URL ngắn hạn để phát stream video hoặc xem tài liệu; tuyệt đối không lấy được đường dẫn tệp gốc trên S3.
* **Instructor (Owner & Co-Instructor):**
  * **Owner:** Toàn quyền CRUD trên khóa học, quản lý module, bài học, tài liệu; quản lý danh sách Co-Instructor; thay đổi trạng thái vòng đời (`DRAFT` $\rightarrow$ `PUBLISHED` $\rightarrow$ `ARCHIVED`).
  * **Co-Instructor:** Được thêm/sửa/sắp xếp module, bài học, nội dung bài giảng; không được xóa khóa học và không được đổi quyền sở hữu.
  * Cấu hình điều kiện hoàn thành bài học trong `lesson_completion_rules`.
* **Admin:**
  * Xem danh sách tổng quan các khóa học trên hệ thống.
  * Có quyền chuyển trạng thái khóa học sang `DISABLED` nếu phát hiện nội dung vi phạm bản quyền hoặc thuần phong mỹ tục. Không trực tiếp sửa nội dung bài giảng.

### 3. Danh sách các bảng liên quan
* `courses`
* `course_instructors`
* `course_modules`
* `lessons`
* `lesson_contents`
* `lesson_completion_rules`

### 4. Chi tiết cấu trúc các bảng

#### Bảng `courses` (Thông tin khóa học)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính duy nhất của khóa học |
| `title` | `varchar(255)` | NOT NULL | Tên tiêu đề khóa học |
| `description` | `text` | Nullable | Mô tả chi tiết nội dung, mục tiêu khóa học |
| `visibility` | `course_visibility` | NOT NULL, Default: `'PUBLIC'` | Chế độ hiển thị (`PUBLIC` xuất hiện trong tìm kiếm, `PRIVATE` ẩn danh) |
| `lifecycle_status` | `course_lifecycle_status`| NOT NULL, Default: `'DRAFT'` | Trạng thái vòng đời (`DRAFT`, `PUBLISHED`, `ARCHIVED`, `DISABLED`) |
| `capacity` | `int` | Nullable | Sĩ số học viên tối đa (NULL nghĩa là không giới hạn) |
| `start_at` | `timestamptz` | Nullable | Thời gian bắt đầu khóa học dự kiến |
| `end_at` | `timestamptz` | Nullable | Thời gian kết thúc khóa học dự kiến |
| `learning_mode` | `learning_mode` | NOT NULL, Default: `'FREE_NAVIGATION'`| Chế độ học (`FREE_NAVIGATION` tự do, `SEQUENTIAL` học tuần tự) |
| `created_by` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Giảng viên tạo và sở hữu khóa học (Owner) |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo khóa học |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm cập nhật thông tin khóa học gần nhất |

#### Bảng `course_instructors` (Phân quyền Giảng viên trợ giảng)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `course_id` | `uuid` | PK, FK $\rightarrow$ `courses(id)` | Khóa ngoại trỏ đến khóa học |
| `instructor_id` | `uuid` | PK, FK $\rightarrow$ `users(id)` | Khóa ngoại trỏ đến giảng viên được phân quyền |
| `course_role` | `course_instructor_role` | NOT NULL, Default: `'CO_INSTRUCTOR'` | Vai trò trong khóa (`OWNER` hoặc `CO_INSTRUCTOR`) |
| `added_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Người phân quyền (chính là Owner) |
| `added_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm được cấp quyền |

#### Bảng `course_modules` (Chương / Học phần khóa học)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của chương học |
| `course_id` | `uuid` | NOT NULL, FK $\rightarrow$ `courses(id)` | Khóa học chứa chương này |
| `title` | `varchar(255)` | NOT NULL | Tiêu đề chương học |
| `description` | `text` | Nullable | Tóm tắt mục tiêu kiến thức của chương |
| `position` | `int` | NOT NULL, Default: `0` | Thứ tự sắp xếp hiển thị trên giao diện |
| `is_default` | `boolean` | NOT NULL, Default: `false` | Đánh dấu module mặc định (dùng khi khóa học không phân chia chương) |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo chương học |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm cập nhật chương học |

#### Bảng `lessons` (Bài học)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của bài học |
| `module_id` | `uuid` | NOT NULL, FK $\rightarrow$ `course_modules(id)` | Chương học chứa bài học này |
| `title` | `varchar(255)` | NOT NULL | Tiêu đề bài học |
| `description` | `text` | Nullable | Ghi chú hoặc hướng dẫn học bài |
| `position` | `int` | NOT NULL, Default: `0` | Thứ tự sắp xếp hiển thị trong module |
| `is_required` | `boolean` | NOT NULL, Default: `true` | Đánh dấu bài học bắt buộc để tính hoàn thành khóa |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo bài học |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm cập nhật bài học gần nhất |

#### Bảng `lesson_contents` (Nội dung bài học & Quản lý Media S3/CDN)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính định danh khối nội dung |
| `lesson_id` | `uuid` | NOT NULL, FK $\rightarrow$ `lessons(id)` | Bài học chứa nội dung này |
| `content_type` | `content_type` | NOT NULL | Phân loại nội dung (`VIDEO`, `PDF`, `SLIDE`, `TEXT`, `LINK`, `FILE`) |
| `title` | `varchar(255)` | Nullable | Tiêu đề khối nội dung hiển thị |
| `text_content` | `text` | Nullable | Nội dung định dạng Markdown/HTML nếu `content_type = 'TEXT'` |
| `external_url` | `varchar(1000)` | Nullable | Đường link ngoài nếu `content_type = 'LINK'` |
| `object_key` | `varchar(500)` | Nullable | Đường dẫn định danh tệp tin trên AWS S3 Bucket |
| `file_name` | `varchar(255)` | Nullable | Tên gốc của tệp khi người dùng tải lên |
| `mime_type` | `varchar(100)` | Nullable | Định dạng tệp tin (ví dụ: `video/mp4`, `application/pdf`) |
| `file_size` | `bigint` | Nullable | Dung lượng tệp tính theo bytes |
| `duration_seconds`| `int` | Nullable | Thời lượng video tính theo giây (phục vụ tính % tiến độ) |
| `upload_status` | `upload_status` | NOT NULL, Default: `'PENDING'` | Trạng thái upload S3 (`PENDING`, `UPLOADED`, `AVAILABLE`, `FAILED`) |
| `position` | `int` | NOT NULL, Default: `0` | Thứ tự hiển thị nội dung trong bài học |
| `version_number` | `int` | NOT NULL, Default: `1` | Số hiệu phiên bản chỉnh sửa |
| `created_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Giảng viên tải lên nội dung |
| `updated_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Giảng viên chỉnh sửa nội dung gần nhất |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo nội dung |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm cập nhật nội dung |

#### Bảng `lesson_completion_rules` (Quy tắc hoàn thành bài học)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của quy tắc |
| `lesson_id` | `uuid` | NOT NULL, UNIQUE, FK $\rightarrow$ `lessons(id)` | Bài học áp dụng quy tắc (Quan hệ 1-1 chặt chẽ) |
| `minimum_time_seconds` | `int` | NOT NULL, Default: `0` | Số giây tối thiểu học viên phải ở lại trang để được tính hoàn thành |
| `min_video_percentage` | `decimal(5, 2)` | Default: `80.0` | Tỷ lệ % tối thiểu của video phải xem (ví dụ: xem $\ge 80\%$) |
| `require_quiz` | `boolean` | NOT NULL, Default: `false` | Bắt buộc phải vượt qua Quiz đính kèm bài học mới hoàn thành |
| `require_assignment` | `boolean` | NOT NULL, Default: `false` | Bắt buộc phải nộp bài tập tự luận mới hoàn thành |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo quy tắc |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm cấu hình quy tắc |

---

# MODULE 3: GHI DANH & THEO DÕI TIẾN ĐỘ HỌC TẬP (ENROLLMENT & PROGRESS TRACKING)

### 1. Mục đích & Vai trò trong hệ thống
* **Mục đích:** Xử lý ghi danh học viên vào khóa học công khai, kiểm soát giới hạn sĩ số (Capacity Lock), và theo dõi lộ trình học tập chi tiết của từng học viên.
* **Vai trò:** Đường truyền nóng (Hot Path) chịu tải ghi dữ liệu cao nhất của hệ thống (~50 RPS khi 1000 người học đồng thời). Áp dụng kỹ thuật Idempotent Upsert để bảo toàn tính toàn vẹn dữ liệu, chống mất mát hoặc cộng dồn trùng thời gian học khi mạng chập chờn.

### 2. Vai trò & Quyền hạn theo Role (RBAC & ABAC)
* **Student:**
  * Ghi danh vào khóa học công khai (`PUBLIC`) đang mở (`PUBLISHED`).
  * Gửi nhịp cập nhật tiến độ học tập (vị trí video, thời lượng học tích lũy) theo chu kỳ 15–20 giây.
  * Tự rút khỏi khóa học (`WITHDRAWN`). Khi rút, mất quyền truy cập tài liệu nhưng lịch sử học cũ được đóng băng.
* **Instructor:**
  * Giám sát tiến độ học tập của từng học viên và tỷ lệ hoàn thành chung của toàn bộ lớp học.
  * Được phép loại học viên vi phạm quy chế ra khỏi khóa học (`REMOVED`).
  * Tuyệt đối không can thiệp bằng tay để đánh dấu bài học hoàn thành thay học viên.
* **Admin:**
  * Xem báo cáo thống kê tỷ lệ ghi danh, số lượng hoàn thành trên quy mô toàn trường.

### 3. Danh sách các bảng liên quan
* `enrollments`
* `lesson_progress`

### 4. Chi tiết cấu trúc các bảng

#### Bảng `enrollments` (Quản lý ghi danh khóa học)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của bản ghi ghi danh |
| `course_id` | `uuid` | NOT NULL, FK $\rightarrow$ `courses(id)` | Khóa học được ghi danh |
| `student_id` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Học viên thực hiện ghi danh |
| `status` | `enrollment_status` | NOT NULL, Default: `'ACTIVE'` | Trạng thái ghi danh (`ACTIVE`, `COMPLETED`, `WITHDRAWN`, `REMOVED`) |
| `created_via` | `varchar(50)` | Default: `'DIRECT'` | Kênh ghi danh (`DIRECT`, `INVITE_CODE`, `ADMIN_ASSIGN`) |
| `enrolled_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm chính thức bắt đầu học |
| `completed_at` | `timestamptz` | Nullable | Thời điểm học viên hoàn thành tất cả bài học bắt buộc |
| `withdrawn_at` | `timestamptz` | Nullable | Thời điểm học viên chủ động hủy ghi danh |
| `removed_at` | `timestamptz` | Nullable | Thời điểm bị giảng viên loại khỏi lớp học |

*Ràng buộc đặc biệt:* `UNIQUE(course_id, student_id)` đảm bảo mỗi học viên chỉ có duy nhất 1 bản ghi ghi danh cho mỗi khóa học trong phạm vi MVP.

#### Bảng `lesson_progress` (Đường truyền nóng theo dõi tiến độ bài học)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của bản ghi tiến độ |
| `enrollment_id` | `uuid` | NOT NULL, FK $\rightarrow$ `enrollments(id)` | Liên kết tới phiên ghi danh của học viên |
| `lesson_id` | `uuid` | NOT NULL, FK $\rightarrow$ `lessons(id)` | Bài học đang được theo dõi tiến độ |
| `status` | `lesson_progress_status` | NOT NULL, Default: `'NOT_STARTED'` | Trạng thái hoàn thành (`NOT_STARTED`, `IN_PROGRESS`, `COMPLETED`) |
| `time_spent_seconds`| `int` | NOT NULL, Default: `0` | Tổng thời gian tích lũy học viên thực sự ở trên bài học (giây) |
| `last_content_id` | `uuid` | Nullable, FK $\rightarrow$ `lesson_contents(id)`| Tab nội dung học viên đang xem dở (phục vụ resume giao diện) |
| `started_at` | `timestamptz` | Nullable | Thời điểm lần đầu tiên học viên mở bài học |
| `completed_at` | `timestamptz` | Nullable | Thời điểm bài học đạt đủ tiêu chuẩn hoàn thành |
| `last_accessed_at` | `timestamptz` | NOT NULL, Default: `now()` | Mốc thời gian lần cuối cùng client gửi tín hiệu học tập |

*Ràng buộc & Index:* `UNIQUE(enrollment_id, lesson_id)` phục vụ câu lệnh Idempotent Upsert `ON CONFLICT DO UPDATE`.

---

# MODULE 4: ĐỘNG CƠ THI TRẮC NGHIỆM & BẢN CHỤP BẤT BIẾN (ASSESSMENT - QUIZ ENGINE)

### 1. Mục đích & Vai trò trong hệ thống
* **Mục đích:** Cung cấp động cơ tạo đề, tổ chức thi trắc nghiệm trực tuyến đồng thời (Peak Concurrency), chấm điểm tự động và bảo vệ tính toàn vẹn của kết quả thi.
* **Vai trò:** Trọng tâm đánh giá năng lực của học viên. Chịu trách nhiệm bảo vệ an toàn đề thi theo nguyên tắc Zero-Trust Client (giờ thi do server quyết định, không gửi đáp án đúng về client, chụp snapshot đề thi để chống thay đổi dữ liệu lịch sử và ngăn chặn nộp bài trùng lặp bằng idempotency key).

### 2. Vai trò & Quyền hạn theo Role (RBAC & ABAC)
* **Student:**
  * Tham gia làm bài thi trắc nghiệm khi khóa học `ACTIVE` và trong thời gian mở đề.
  * Chỉ được phép làm bài trong giới hạn `max_attempts`.
  * Nhận kết quả tự động sau khi nộp bài; xem lại bài làm theo chính sách hiển thị của giảng viên (`show_score`, `show_correct_answers`).
* **Instructor:**
  * Soạn câu hỏi cố định, cấu hình thời gian làm bài (`time_limit_seconds`), hạn nộp (`deadline`), chính sách tính điểm (`grading_policy`).
  * Tự động bị khóa sửa câu hỏi (`is_locked = true`) ngay khi có attempt đầu tiên của học viên.
  * Có quyền điều chỉnh điểm (`final_score`) khi giải quyết khiếu nại của học viên kèm ghi chú lý do bắt buộc.
* **Admin:**
  * Giám sát kết quả thống kê các bài thi, không tham gia chấm hoặc sửa đề thi.

### 3. Danh sách các bảng liên quan
* `quizzes`
* `question_banks`
* `questions`
* `question_options`
* `quiz_questions`
* `quiz_attempts`
* `quiz_answers`
* `quiz_results`

### 4. Chi tiết cấu trúc các bảng

#### Bảng `quizzes` (Cấu hình bài thi trắc nghiệm)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của bài trắc nghiệm |
| `course_id` | `uuid` | NOT NULL, FK $\rightarrow$ `courses(id)` | Khóa học chứa bài quiz |
| `lesson_id` | `uuid` | Nullable, FK $\rightarrow$ `lessons(id)` | Bài học chứa quiz (nếu là quiz cuối bài, NULL nếu là quiz cấp môn) |
| `title` | `varchar(255)` | NOT NULL | Tên tiêu đề bài thi trắc nghiệm |
| `description` | `text` | Nullable | Hướng dẫn, quy chế thi |
| `question_source` | `varchar(50)` | Default: `'FIXED'` | Nguồn câu hỏi (`FIXED` cố định, `BANK` từ ngân hàng câu hỏi) |
| `time_limit_seconds`| `int` | Nullable | Thời gian làm bài tính bằng giây (NULL nghĩa là không giới hạn) |
| `deadline` | `timestamptz` | Nullable | Hạn chót đóng cổng nộp bài thi |
| `max_attempts` | `int` | NOT NULL, Default: `1` | Số lần tối đa học viên được phép làm bài |
| `grading_policy` | `quiz_grading_policy` | NOT NULL, Default: `'HIGHEST'`| Cách chọn điểm tổng kết (`HIGHEST`, `LATEST`, `AVERAGE`) |
| `randomize_questions`| `boolean` | Default: `false` | Đảo ngẫu nhiên thứ tự câu hỏi khi phát đề |
| `randomize_answers` | `boolean` | Default: `false` | Đảo ngẫu nhiên thứ tự các phương án lựa chọn |
| `show_score` | `boolean` | Default: `true` | Cho phép học viên xem điểm số ngay sau khi nộp bài |
| `show_correct_answers`| `boolean` | Default: `false` | Cho phép học viên xem đáp án đúng sau khi kết thúc bài thi |
| `is_locked` | `boolean` | NOT NULL, Default: `false` | Cờ khóa đề (tự kích hoạt khi có attempt đầu tiên để bảo vệ bài thi) |
| `created_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Giảng viên tạo đề thi |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo bài thi |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm cập nhật bài thi gần nhất |

#### Bảng `question_banks` (Ngân hàng câu hỏi của khóa học)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của ngân hàng câu hỏi |
| `course_id` | `uuid` | NOT NULL, FK $\rightarrow$ `courses(id)` | Khóa học sở hữu ngân hàng câu hỏi (không dùng chung cross-course) |
| `name` | `varchar(255)` | NOT NULL | Tên danh mục ngân hàng câu hỏi |
| `created_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Giảng viên khởi tạo |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo ngân hàng đề |

#### Bảng `questions` (Kho câu hỏi trắc nghiệm)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của câu hỏi |
| `question_bank_id` | `uuid` | Nullable, FK $\rightarrow$ `question_banks(id)`| Ngân hàng chứa câu hỏi (NULL nếu gắn trực tiếp vào Quiz) |
| `question_type` | `question_type` | NOT NULL, Default: `'SINGLE_CHOICE'` | Dạng câu hỏi (`TRUE_FALSE`, `SINGLE_CHOICE`, `MULTIPLE_CHOICE`) |
| `question_text` | `text` | NOT NULL | Nội dung câu hỏi |
| `keyword_answer` | `varchar(255)` | Nullable | Đáp án từ khóa nếu là dạng `KEYWORD_FILL` (thuộc Backlog) |
| `created_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Giảng viên biên soạn câu hỏi |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo câu hỏi |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm cập nhật câu hỏi |

#### Bảng `question_options` (Phương án lựa chọn trả lời)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của phương án trả lời |
| `question_id` | `uuid` | NOT NULL, FK $\rightarrow$ `questions(id)` | Câu hỏi chứa phương án này |
| `option_text` | `text` | NOT NULL | Nội dung hiển thị của phương án |
| `is_correct` | `boolean` | NOT NULL, Default: `false` | Đánh dấu phương án đúng (tuyệt đối không gửi về client khi làm bài) |
| `position` | `int` | NOT NULL, Default: `0` | Thứ tự hiển thị phương án |

#### Bảng `quiz_questions` (Liên kết bài thi và câu hỏi)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `quiz_id` | `uuid` | PK, FK $\rightarrow$ `quizzes(id)` | Bài thi được gán câu hỏi |
| `question_id` | `uuid` | PK, FK $\rightarrow$ `questions(id)` | Câu hỏi được chọn vào đề |
| `position` | `int` | NOT NULL, Default: `0` | Vị trí thứ tự câu hỏi trong bài thi |
| `points` | `decimal(5, 2)` | NOT NULL, Default: `1.0` | Thang điểm của riêng câu hỏi này trong bài |

#### Bảng `quiz_attempts` (Phiên làm bài thi của học viên)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của lượt làm bài thi |
| `quiz_id` | `uuid` | NOT NULL, FK $\rightarrow$ `quizzes(id)` | Bài thi đang thực hiện |
| `enrollment_id` | `uuid` | NOT NULL, FK $\rightarrow$ `enrollments(id)` | Khóa ngoại liên kết tới học viên thông qua enrollment |
| `attempt_number` | `int` | NOT NULL | Lần thi thứ mấy (1, 2, 3...) |
| `status` | `quiz_attempt_status` | NOT NULL, Default: `'IN_PROGRESS'` | Trạng thái lượt thi (`IN_PROGRESS`, `SUBMITTED`, `TIMED_OUT`) |
| `started_at` | `timestamptz` | NOT NULL, Default: `now()` | Giờ bắt đầu tính từ Server để chống gian lận thời gian |
| `submitted_at` | `timestamptz` | Nullable | Thời điểm học viên bấm nộp bài thực tế |
| `snapshot_payload`| `jsonb` | Nullable | Bản chụp JSON bất biến lưu toàn bộ đề thi, thứ tự và đáp án tại lúc thi |
| `idempotency_key` | `varchar(255)` | UNIQUE, Nullable | Khóa chống nộp trùng lặp khi người dùng bấm đúp chuột hoặc retry mạng |

*Ràng buộc:* `UNIQUE(quiz_id, enrollment_id, attempt_number)` đảm bảo không thể tạo 2 attempt cùng số thứ tự.

#### Bảng `quiz_answers` (Chi tiết câu trả lời của học viên)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của câu trả lời |
| `attempt_id` | `uuid` | NOT NULL, FK $\rightarrow$ `quiz_attempts(id)`| Lượt thi chứa câu trả lời này |
| `question_id` | `uuid` | NOT NULL, FK $\rightarrow$ `questions(id)` | Câu hỏi được trả lời |
| `selected_option_data`| `jsonb` | Nullable | Lưu ID hoặc mảng các ID của `question_options` mà học viên đã chọn |
| `text_answer` | `text` | Nullable | Câu trả lời dạng text (dùng cho dạng điền từ) |
| `is_correct` | `boolean` | Nullable | Kết quả máy chấm: Đúng (`true`) hoặc Sai (`false`) |
| `awarded_score` | `decimal(5, 2)` | Nullable | Điểm số đạt được của câu hỏi này |

*Ràng buộc:* `UNIQUE(attempt_id, question_id)` ngăn chặn một câu hỏi bị lưu đáp án 2 lần trong cùng một lượt thi.

#### Bảng `quiz_results` (Kết quả tổng kết & Điều chỉnh điểm)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của bản ghi kết quả |
| `attempt_id` | `uuid` | NOT NULL, UNIQUE, FK $\rightarrow$ `quiz_attempts(id)` | Lượt thi được tổng kết kết quả |
| `auto_score` | `decimal(5, 2)` | NOT NULL | Điểm số do hệ thống tự động chấm theo thuật toán |
| `final_score` | `decimal(5, 2)` | NOT NULL | Điểm số cuối cùng sau khi giảng viên phúc khảo/điều chỉnh |
| `adjusted_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Giảng viên thực hiện sửa đổi điểm |
| `adjustment_reason`| `text` | Nullable | Lý do giải trình việc can thiệp điểm số (Audit Trail) |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm sinh kết quả điểm |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm điều chỉnh điểm số |

---

# MODULE 5: BÀI TẬP TỰ LUẬN & CHẤM ĐIỂM (ASSESSMENT - ASSIGNMENT ENGINE)

### 1. Mục đích & Vai trò trong hệ thống
* **Mục đích:** Hỗ trợ giảng viên giao bài tập tự luận (yêu cầu nộp văn bản hoặc tải file đính kèm), quản lý hạn nộp chặt chẽ và cung cấp giao diện chấm điểm, phản hồi cho từng học viên.
* **Vai trò:** Xử lý việc tải lên tài liệu dung lượng lớn của học viên lên S3 thông qua Presigned URL. Áp dụng quy tắc nộp đè bài mới nhất (1 dòng submission cho mỗi học viên) để tối ưu dung lượng lưu trữ cơ sở dữ liệu và hỗ trợ cơ chế chấm lại (Regrade).

### 2. Vai trò & Quyền hạn theo Role (RBAC & ABAC)
* **Student:**
  * Xem đề bài tập, tải file tài liệu đính kèm.
  * Tải file bài nộp lên S3 qua Presigned URL và xác nhận nộp bài trước `deadline`.
  * Nộp lại bài nếu chưa vượt quá `max_submissions` (mặc định 1 bản ghi nộp mới nhất đè lên bản cũ).
  * Xem điểm số và nhận xét của chính mình sau khi giảng viên đã chấm.
* **Instructor:**
  * Tạo đề bài tập, thiết lập hạn nộp, cấu hình thang điểm tối đa (`max_score`), cho phép hoặc cấm nộp muộn (`allow_late_submission`).
  * Tải và xem toàn bộ bài nộp của học viên trong khóa học.
  * Chấm điểm, nhập nhận xét; có quyền chấm lại (Regrade) khi học viên khiếu nại (lưu vết `previous_score`).
* **Admin:**
  * Xem tổng số lượng bài tập và tình trạng bài nộp chờ chấm ở góc độ vận hành hệ thống.

### 3. Danh sách các bảng liên quan
* `assignments`
* `assignment_submissions`
* `submission_files`
* `assignment_grades`

### 4. Chi tiết cấu trúc các bảng

#### Bảng `assignments` (Thông tin bài tập tự luận)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của bài tập tự luận |
| `course_id` | `uuid` | NOT NULL, FK $\rightarrow$ `courses(id)` | Khóa học chứa bài tập |
| `lesson_id` | `uuid` | Nullable, FK $\rightarrow$ `lessons(id)` | Bài học cụ thể chứa bài tập (NULL nếu là bài tập lớn cấp môn) |
| `title` | `varchar(255)` | NOT NULL | Tên tiêu đề bài tập |
| `instructions` | `text` | Nullable | Đề bài, yêu cầu chi tiết và tiêu chí chấm điểm |
| `deadline` | `timestamptz` | Nullable | Hạn nộp bài cuối cùng tính theo giờ chuẩn server |
| `max_submissions` | `int` | NOT NULL, Default: `1` | Số lần nộp bài tối đa được phép nộp đè |
| `allow_late_submission`| `boolean`| NOT NULL, Default: `false` | Cho phép nộp sau hạn chót (hệ thống sẽ đánh dấu `is_late = true`) |
| `allow_text` | `boolean` | NOT NULL, Default: `true` | Cho phép nhập câu trả lời trực tiếp dạng văn bản |
| `allow_files` | `boolean` | NOT NULL, Default: `true` | Cho phép tải tệp đính kèm lên hệ thống |
| `max_score` | `decimal(5, 2)` | NOT NULL, Default: `100.0` | Thang điểm tối đa của bài tập |
| `created_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Giảng viên tạo đề bài tập |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo bài tập |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm chỉnh sửa bài tập |

#### Bảng `assignment_submissions` (Bài nộp của học viên)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của bản nộp bài |
| `assignment_id` | `uuid` | NOT NULL, FK $\rightarrow$ `assignments(id)`| Bài tập được nộp |
| `enrollment_id` | `uuid` | NOT NULL, FK $\rightarrow$ `enrollments(id)`| Phiên ghi danh của học viên nộp bài |
| `text_content` | `text` | Nullable | Nội dung câu trả lời dạng văn bản |
| `submission_count` | `int` | NOT NULL, Default: `1` | Số thứ tự lần nộp hiện tại (tăng lên khi nộp lại) |
| `status` | `assignment_status` | NOT NULL, Default: `'SUBMITTED'` | Trạng thái (`SUBMITTED`, `PENDING_REGRADE`, `GRADED`) |
| `submitted_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm nộp bài tính theo giờ máy chủ |
| `is_late` | `boolean` | NOT NULL, Default: `false` | Cờ tự động bật nếu `submitted_at > assignments.deadline` |

*Ràng buộc:* `UNIQUE(assignment_id, enrollment_id)` duy trì đúng 1 bản ghi nộp mới nhất duy nhất cho mỗi học viên.

#### Bảng `submission_files` (Tệp tin đính kèm bài nộp)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của tệp đính kèm |
| `submission_id` | `uuid` | NOT NULL, FK $\rightarrow$ `assignment_submissions(id)`| Bài nộp sở hữu tệp tin này |
| `object_key` | `varchar(500)` | NOT NULL | Khóa định danh tệp tin lưu trữ trên AWS S3 Bucket |
| `file_name` | `varchar(255)` | NOT NULL | Tên tệp tin gốc khi tải lên |
| `file_type` | `varchar(100)` | Nullable | Định dạng file (MIME type: `application/zip`, `application/pdf`...) |
| `file_size` | `bigint` | NOT NULL | Kích thước file tính theo bytes |
| `upload_status` | `upload_status` | NOT NULL, Default: `'PENDING'` | Trạng thái xử lý file (`PENDING`, `UPLOADED`, `AVAILABLE`, `FAILED`) |
| `uploaded_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm hoàn tất tải lên S3 |

#### Bảng `assignment_grades` (Điểm số & Nhận xét bài tập)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của bản ghi điểm số |
| `submission_id` | `uuid` | NOT NULL, UNIQUE, FK $\rightarrow$ `assignment_submissions(id)`| Bài nộp được chấm (Quan hệ 1-1) |
| `score` | `decimal(5, 2)` | NOT NULL | Điểm số giảng viên chấm cho bài nộp |
| `feedback` | `text` | Nullable | Nhận xét chi tiết của giảng viên |
| `graded_by` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Giảng viên trực tiếp chấm bài |
| `graded_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm hoàn tất chấm bài |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm cập nhật điểm gần nhất |
| `previous_score` | `decimal(5, 2)` | Nullable | Điểm số cũ trước khi chấm lại (Audit nhẹ) |
| `regraded_at` | `timestamptz` | Nullable | Thời điểm chấm lại bài |
| `regrade_reason` | `text` | Nullable | Lý do giải trình việc chấm lại điểm bài tập |

---

# MODULE 6: THÔNG BÁO BẤT ĐỒNG BỘ & ĐỘ TIN CẬY HỆ THỐNG (ASYNC NOTIFICATIONS, OUTBOX & AUDIT)

### 1. Mục đích & Vai trò trong hệ thống
* **Mục đích:** Xử lý toàn bộ luồng thông báo trong ứng dụng (In-app Notifications) thông qua kiến trúc xử lý sự kiện bất đồng bộ (Event-driven với SQS/Queue), chống mất mát thông điệp bằng Transactional Outbox Pattern, và ghi nhận vết kiểm toán (Audit Trail) cho các hành động quản trị nhạy cảm.
* **Vai trò:** Trụ cột đảm bảo độ tin cậy của hệ thống (System Reliability & Decoupling). Đảm bảo các tác vụ tốn thời gian không làm nghẽn luồng xử lý chính của người dùng, worker hoạt động idempotent nhờ khóa `dedupe_key` độc nhất.

### 2. Vai trò & Quyền hạn theo Role (RBAC & ABAC)
* **Student:**
  * Nhận thông báo trong ứng dụng về các sự kiện: có bài quiz mới, bài tập đã được chấm, khóa học có cập nhật.
  * Polling danh sách thông báo chưa đọc (`is_read = false`) theo chu kỳ 30–60 giây.
  * Đánh dấu đã đọc cho các thông báo thuộc sở hữu của chính mình (`user_id = current_user.id`).
* **Instructor:**
  * Nhận thông báo khi học viên nộp bài tập, khi có khiếu nại điểm số hoặc các cập nhật quan trọng của khóa học.
* **Admin:**
  * Xem toàn bộ nhật ký kiểm toán trong `audit_logs` để truy vết các hành động quản trị nhạy cảm (khóa tài khoản, vô hiệu hóa khóa học, điều chỉnh điểm).

### 3. Danh sách các bảng liên quan
* `notifications`
* `outbox_events`
* `audit_logs`

### 4. Chi tiết cấu trúc các bảng

#### Bảng `notifications` (Hộp thư thông báo trong ứng dụng)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của thông báo |
| `user_id` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Người dùng nhận thông báo |
| `event_type` | `varchar(100)` | NOT NULL | Loại sự kiện (`QUIZ_PUBLISHED`, `ASSIGNMENT_GRADED`...) |
| `title` | `varchar(255)` | NOT NULL | Tiêu đề thông báo ngắn gọn |
| `message` | `text` | NOT NULL | Nội dung chi tiết của thông báo |
| `dedupe_key` | `varchar(255)` | NOT NULL, UNIQUE | Khóa chống trùng thông điệp khi Worker retry nhiều lần |
| `target_type` | `varchar(50)` | Nullable | Loại thực thể đích (`COURSE`, `LESSON`, `QUIZ`, `ASSIGNMENT`) |
| `target_id` | `uuid` | Nullable | ID của thực thể đích phục vụ điều hướng màn hình khi người dùng bấm vào |
| `action_url` | `varchar(500)` | Nullable | Đường dẫn điều hướng deep-link trực tiếp trên giao diện Client SPA |
| `metadata` | `jsonb` | Nullable | Dữ liệu ngữ cảnh phong phú phục vụ render notification card (điểm số, tiêu đề...) |
| `is_read` | `boolean` | NOT NULL, Default: `false` | Đánh dấu người dùng đã đọc thông báo hay chưa |
| `read_at` | `timestamptz` | Nullable | Thời điểm người dùng mở xem thông báo |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo thông báo |

*Index tối ưu:* `indexes { (user_id, is_read, created_at) }` tối ưu hóa đường truyền nóng học viên Polling kiểm tra thông báo chưa đọc.

#### Bảng `outbox_events` (Bảng trung chuyển Transactional Outbox)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của sự kiện outbox |
| `aggregate_type` | `varchar(100)` | NOT NULL | Tên tập hợp dữ liệu phát sinh sự kiện (`COURSE`, `USER`, `ASSIGNMENT`) |
| `aggregate_id` | `uuid` | NOT NULL | ID của bản ghi nghiệp vụ liên quan |
| `event_type` | `varchar(100)` | NOT NULL | Mã định danh sự kiện miền (Domain Event) |
| `payload` | `jsonb` | NOT NULL | Toàn bộ dữ liệu chi tiết của sự kiện đóng gói dưới dạng JSONB |
| `status` | `outbox_status` | NOT NULL, Default: `'PENDING'` | Trạng thái gửi đi (`PENDING`, `PROCESSED`, `FAILED`) |
| `retry_count` | `int` | NOT NULL, Default: `0` | Số lần đã thử gửi lại khi gặp sự cố mạng |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm sự kiện được ghi vào DB trong cùng transaction nghiệp vụ |
| `processed_at` | `timestamptz` | Nullable | Thời điểm Outbox Worker đẩy sự kiện thành công vào Queue (SQS) |

#### Bảng `audit_logs` (Nhật ký kiểm toán hành vi quản trị)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của dòng nhật ký kiểm toán |
| `actor_id` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Người thực hiện hành động (Admin hoặc Instructor) |
| `action` | `varchar(100)` | NOT NULL | Hành động nghiệp vụ (`DISABLE_USER`, `DISABLE_COURSE`, `REGRADE`) |
| `target_type` | `varchar(50)` | NOT NULL | Đối tượng bị tác động (`USER`, `COURSE`, `ASSIGNMENT`) |
| `target_id` | `uuid` | NOT NULL | ID của đối tượng bị tác động |
| `metadata` | `jsonb` | Nullable | Lưu trữ trạng thái cũ, trạng thái mới và lý do can thiệp dưới dạng JSON |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm phát sinh hành động kiểm toán |

---

# MODULE 7: NHẬT KÝ THAY ĐỔI & LỊCH SỬ NỘI DUNG (CONTENT AUDITING & VERSIONING - BACKLOG)

### 1. Mục đích & Vai trò trong hệ thống
* **Mục đích:** Lưu vết các hành động thêm, sửa, xóa nội dung bài học của giảng viên và quản lý số hiệu phiên bản (`version_number`).
* **Vai trò:** Thuộc phạm vi **BACKLOG**. Hệ thống không lưu toàn bộ nội dung của phiên bản cũ (tránh phình to dung lượng) mà chỉ lưu vết ai đã sửa, hành động gì và vào thời điểm nào nhằm phục vụ đối soát nội dung khi có tranh chấp.

### 2. Vai trò & Quyền hạn theo Role (RBAC)
* **Student:** Không có quyền truy cập bảng này.
* **Instructor:** Xem lại lịch sử các lần chỉnh sửa nội dung bài học của chính mình hoặc Co-Instructor.
* **Admin:** Truy vấn kiểm tra lịch sử thay đổi nội dung khi cần xác minh khiếu nại vi phạm.

### 3. Danh sách các bảng liên quan
* `content_change_logs`

### 4. Chi tiết cấu trúc các bảng

#### Bảng `content_change_logs` (Lịch sử chỉnh sửa bài học)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của bản ghi nhật ký |
| `content_id` | `uuid` | NOT NULL, FK $\rightarrow$ `lesson_contents(id)`| Khối nội dung bài học bị tác động |
| `changed_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Giảng viên thực hiện thao tác thay đổi |
| `action` | `varchar(50)` | NOT NULL | Loại thao tác (`CREATE`, `UPDATE_TEXT`, `REPLACE_FILE`, `DELETE`) |
| `version_number` | `int` | NOT NULL | Số hiệu phiên bản tại thời điểm ghi nhận |
| `changed_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm phát sinh chỉnh sửa |

---

# MODULE 8: ĐÁNH GIÁ KHÓA HỌC & KÊNH TRAO ĐỔI (COURSE REVIEWS & COMMUNICATION - BACKLOG)

### 1. Mục đích & Vai trò trong hệ thống
* **Mục đích:** Hỗ trợ tính năng đánh giá (Review/Rating) khóa học và cung cấp kênh chat trao đổi hai chiều giữa học viên và giảng viên trong nội bộ khóa học.
* **Vai trò:** Thuộc phạm vi **BACKLOG** (Do đề tài yêu cầu loại bỏ tính năng chat thời gian thực phức tạp để tập trung vào các tiêu chí cốt lõi). Bản thiết kế giữ nguyên cấu trúc dữ liệu để sẵn sàng triển khai ở Phase 2.

### 2. Vai trò & Quyền hạn theo Role (RBAC)
* **Student:**
  * Chỉ được đánh giá (`course_reviews`) khi đã hoàn thành khóa học (`enrollments.status = 'COMPLETED'`).
  * Gửi tin nhắn, chia sẻ tài liệu trong kênh trao đổi của khóa học mình đang học.
* **Instructor:**
  * Theo dõi đánh giá của học viên; không trực tiếp phản hồi review (đảm bảo tính khách quan).
  * Trao đổi, giải đáp thắc mắc của học viên trong kênh chat của khóa học.
* **Admin:**
  * Kiểm duyệt, ẩn (`is_hidden = true`) các đánh giá có nội dung tiêu cực hoặc vi phạm tiêu chuẩn cộng đồng.

### 3. Danh sách các bảng liên quan
* `course_reviews`
* `course_channels`
* `messages`
* `message_attachments`

### 4. Chi tiết cấu trúc các bảng

#### Bảng `course_reviews` (Đánh giá & Nhận xét khóa học)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của đánh giá |
| `course_id` | `uuid` | NOT NULL, FK $\rightarrow$ `courses(id)` | Khóa học được đánh giá |
| `student_id` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Học viên gửi đánh giá |
| `rating` | `decimal(2, 1)` | NOT NULL | Số sao đánh giá từ 0.5 đến 5.0 |
| `comment` | `text` | Nullable | Nội dung nhận xét chi tiết |
| `is_hidden` | `boolean` | NOT NULL, Default: `false` | Cờ ẩn đánh giá do vi phạm quy chế |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm viết đánh giá |
| `updated_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm chỉnh sửa nhận xét |
| `moderated_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Admin thực hiện kiểm duyệt đánh giá |
| `moderated_at` | `timestamptz` | Nullable | Thời điểm kiểm duyệt |

*Ràng buộc:* `UNIQUE(course_id, student_id)` đảm bảo mỗi học viên chỉ được đánh giá khóa học đúng 1 lần.

#### Bảng `course_channels` (Kênh trao đổi khóa học)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của kênh chat |
| `course_id` | `uuid` | NOT NULL, UNIQUE, FK $\rightarrow$ `courses(id)`| Khóa học sở hữu kênh trao đổi (Mỗi khóa có 1 kênh duy nhất) |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo kênh |

#### Bảng `messages` (Tin nhắn trao đổi)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của tin nhắn |
| `channel_id` | `uuid` | NOT NULL, FK $\rightarrow$ `course_channels(id)`| Kênh chat chứa tin nhắn |
| `sender_id` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Người gửi tin nhắn (học viên hoặc giảng viên) |
| `content` | `text` | NOT NULL | Nội dung văn bản của tin nhắn |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm gửi tin nhắn |
| `edited_at` | `timestamptz` | Nullable | Thời điểm chỉnh sửa tin nhắn gần nhất |
| `deleted_at` | `timestamptz` | Nullable | Thời điểm xóa mềm tin nhắn (UI vẫn hiển thị trạng thái đã xóa) |

#### Bảng `message_attachments` (Tệp tin đính kèm tin nhắn)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của tệp đính kèm |
| `message_id` | `uuid` | NOT NULL, FK $\rightarrow$ `messages(id)` | Tin nhắn sở hữu tệp đính kèm |
| `object_key` | `varchar(500)` | NOT NULL | Khóa định danh file trên AWS S3 |
| `file_name` | `varchar(255)` | Nullable | Tên gốc của file |
| `file_type` | `varchar(100)` | Nullable | Định dạng file (MIME type) |
| `file_size` | `bigint` | Nullable | Kích thước file tính theo bytes |

---

# MODULE 9: MỞ RỘNG ĐĂNG KÝ, MÃ MỜI & EMAIL SỰ KIỆN (ADVANCED ONBOARDING & INVITATIONS - BACKLOG)

### 1. Mục đích & Vai trò trong hệ thống
* **Mục đích:** Quản lý quy trình ứng tuyển giảng viên (Instructor Application), cơ chế mời và tham gia khóa học riêng tư (`PRIVATE`) qua mã mời/lời mời trực tiếp, cùng hệ thống cấu hình thông báo và gửi email tự động.
* **Vai trò:** Thuộc phạm vi **BACKLOG**. Giúp hệ thống hỗ trợ các luồng nghiệp vụ phức tạp ở giai đoạn phát triển mở rộng mà không làm ảnh hưởng đến tiến độ MVP lõi.

### 2. Vai trò & Quyền hạn theo Role (RBAC)
* **Student:**
  * Nhập mã mời (`course_invite_codes`) hoặc chấp nhận lời mời (`course_invitations`) để ghi danh vào khóa học riêng tư.
  * Tự cấu hình tùy chọn nhận thông báo qua ứng dụng hoặc email trong `notification_preferences`.
* **Instructor:**
  * Nộp hồ sơ ứng tuyển giảng viên (đính kèm CV chứng chỉ).
  * Tạo mã mời hoặc gửi lời mời trực tiếp đến email học viên vào các khóa học riêng tư.
* **Admin:**
  * Xét duyệt hồ sơ ứng tuyển giảng viên (`APPROVED` hoặc `REJECTED`).
  * Quản lý các yêu cầu tham gia khóa học (`enrollment_requests`).

### 3. Danh sách các bảng liên quan
* `instructor_applications`
* `enrollment_requests`
* `course_invite_codes`
* `course_invitations`
* `notification_preferences`
* `email_notification_events`

### 4. Chi tiết cấu trúc các bảng

#### Bảng `instructor_applications` (Hồ sơ ứng tuyển giảng viên)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của hồ sơ |
| `email` | `varchar(255)` | NOT NULL | Email liên lạc của ứng viên |
| `full_name` | `varchar(255)` | NOT NULL | Họ và tên ứng viên |
| `phone` | `varchar(50)` | Nullable | Số điện thoại liên hệ |
| `organization` | `varchar(255)` | Nullable | Cơ quan, trường học đang công tác |
| `job_title` | `varchar(255)` | Nullable | Vị trí, chức danh chuyên môn |
| `expertise` | `varchar(255)` | Nullable | Chuyên ngành giảng dạy chính |
| `years_of_experience`| `int` | Nullable | Số năm kinh nghiệm thực tế |
| `professional_bio` | `text` | Nullable | Giới thiệu chi tiết năng lực chuyên môn |
| `motivation` | `text` | Nullable | Lý do, mục tiêu muốn tham gia giảng dạy |
| `verification_document_object_key`| `varchar(500)`| Nullable | Khóa lưu file CV, bằng cấp trên AWS S3 |
| `status` | `instructor_application_status`| NOT NULL, Default: `'PENDING'`| Trạng thái duyệt hồ sơ (`PENDING`, `APPROVED`, `REJECTED`) |
| `reviewed_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Admin chịu trách nhiệm thẩm định |
| `reviewed_at` | `timestamptz` | Nullable | Thời điểm duyệt hoặc từ chối hồ sơ |
| `rejection_reason` | `text` | Nullable | Lý do từ chối nếu hồ sơ không đạt yêu cầu |
| `submitted_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm nộp hồ sơ |

#### Bảng `enrollment_requests` (Yêu cầu tham gia khóa học riêng tư)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của yêu cầu |
| `course_id` | `uuid` | NOT NULL, FK $\rightarrow$ `courses(id)` | Khóa học riêng tư muốn tham gia |
| `student_id` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Học viên gửi yêu cầu tham gia |
| `status` | `enrollment_request_status`| NOT NULL, Default: `'PENDING'`| Trạng thái xử lý (`PENDING`, `APPROVED`, `REJECTED`, `CANCELLED`) |
| `requested_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm gửi yêu cầu |
| `reviewed_at` | `timestamptz` | Nullable | Thời điểm duyệt yêu cầu |
| `reviewed_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Giảng viên hoặc Admin xét duyệt |
| `rejection_reason` | `text` | Nullable | Lý do từ chối duyệt |

#### Bảng `course_invite_codes` (Mã mời tham gia khóa học)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của mã mời |
| `course_id` | `uuid` | NOT NULL, FK $\rightarrow$ `courses(id)` | Khóa học áp dụng mã mời |
| `code` | `varchar(100)` | NOT NULL, UNIQUE | Chuỗi mã ký tự mời duy nhất (ví dụ: `SUMMER2026`) |
| `expires_at` | `timestamptz` | Nullable | Thời điểm mã mời hết hiệu lực |
| `is_active` | `boolean` | NOT NULL, Default: `true` | Cờ kích hoạt hoặc vô hiệu hóa mã mời |
| `created_by` | `uuid` | Nullable, FK $\rightarrow$ `users(id)` | Giảng viên khởi tạo mã mời |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm tạo mã mời |

#### Bảng `course_invitations` (Lời mời tham gia khóa học gửi đích danh)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của lời mời |
| `course_id` | `uuid` | NOT NULL, FK $\rightarrow$ `courses(id)` | Khóa học được mời tham gia |
| `student_id` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Học viên nhận được lời mời |
| `invited_by` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Giảng viên gửi lời mời |
| `status` | `invitation_status` | NOT NULL, Default: `'PENDING'` | Trạng thái (`PENDING`, `ACCEPTED`, `DECLINED`, `EXPIRED`) |
| `expires_at` | `timestamptz` | Nullable | Hạn chót phản hồi lời mời |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm gửi lời mời |
| `responded_at` | `timestamptz` | Nullable | Thời điểm học viên đồng ý hoặc từ chối |

#### Bảng `notification_preferences` (Tùy chọn nhận thông báo của người dùng)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính cấu hình tùy chọn |
| `user_id` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Người dùng thiết lập cấu hình |
| `event_type` | `varchar(100)` | NOT NULL | Loại sự kiện thông báo |
| `in_app_enabled` | `boolean` | NOT NULL, Default: `true` | Bật/Tắt nhận thông báo trên giao diện web/app |
| `email_enabled` | `boolean` | NOT NULL, Default: `true` | Bật/Tắt nhận email thông báo |
| `reminder_before_minutes`| `int` | Default: `1440` | Thời gian gửi thông báo nhắc trước deadline (phút, mặc định 1 ngày) |

*Ràng buộc:* `UNIQUE(user_id, event_type)` đảm bảo mỗi sự kiện chỉ có 1 bản ghi cấu hình cho mỗi tài khoản.

#### Bảng `email_notification_events` (Hàng đợi sự kiện gửi email)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của bản ghi gửi email |
| `user_id` | `uuid` | NOT NULL, FK $\rightarrow$ `users(id)` | Người nhận email |
| `event_type` | `varchar(100)` | Nullable | Tên sự kiện kích hoạt gửi mail |
| `recipient_email`| `varchar(255)` | Nullable | Địa chỉ email người nhận |
| `dedupe_key` | `varchar(255)` | Nullable, UNIQUE | Khóa chống gửi trùng email khi Worker/Broker retry thông điệp |
| `status` | `notification_delivery_status`| NOT NULL, Default: `'PENDING'`| Trạng thái gửi thư (`PENDING`, `SENT`, `FAILED`) |
| `scheduled_at` | `timestamptz` | Nullable | Thời điểm dự kiến phát thư |
| `sent_at` | `timestamptz` | Nullable | Thời điểm máy chủ SMTP gửi thư thành công |
| `retry_count` | `int` | NOT NULL, Default: `0` | Số lần đã thử gửi lại khi thất bại |

---

# MODULE 10: TRỢ LÝ HỌC TẬP AI TUTOR RAG (AI TUTOR KNOWLEDGE ASSISTANT - STRETCH P5)

### 1. Mục đích & Vai trò trong hệ thống
* **Mục đích:** Cung cấp trợ lý học tập thông minh dựa trên kiến trúc Retrieval-Augmented Generation (RAG), cho phép học viên hỏi đáp về nội dung tài liệu bài giảng của khóa học, nhận câu trả lời có trích dẫn nguồn cụ thể kèm liên kết trực tiếp tới bài học.
* **Vai trò:** Tính năng nâng cao (**STRETCH**) phục vụ cột mốc đánh giá **Deliverable P5 (AI Engineering)**. Sử dụng extension `pgvector` trực tiếp trên PostgreSQL dùng chung để tìm kiếm ngữ nghĩa, trang bị các hàng rào an toàn (Guardrails) nghiêm ngặt để chống gian lận và chống rò rỉ dữ liệu xuyên khóa học.

### 2. Vai trò & Quyền hạn theo Role (RBAC & Guardrails)
* **Student:**
  * Đặt câu hỏi với AI Tutor trên các khóa học mà bản thân đang có `enrollments.status = 'ACTIVE'`.
  * **Hàng rào chống gian lận (Anti-Cheat):** Tự động bị hệ thống **khóa hoàn toàn quyền truy cập AI Tutor** nếu tài khoản đang có bất kỳ bài thi Quiz nào ở trạng thái `IN_PROGRESS`.
  * Nhận câu trả lời kèm danh sách các đoạn trích dẫn (`cited_chunk_ids`) để kiểm chứng nguồn kiến thức.
* **Instructor:**
  * Giám sát các tài liệu được tự động bóc tách text và nhúng vector vào kho tri thức của AI.
  * Tùy chọn bật/tắt quyền sử dụng AI Tutor cho từng bài học cụ thể.
* **Admin:**
  * Giám sát chi phí sử dụng AI thông qua tổng số lượng token đầu vào (`prompt_tokens`), token đầu ra (`completion_tokens`) và độ trễ phản hồi (`latency_ms`).

### 3. Danh sách các bảng liên quan
* `ai_document_chunks`
* `ai_conversations`
* `ai_messages`

### 4. Chi tiết cấu trúc các bảng

#### Bảng `ai_document_chunks` (Kho ngữ đoạn tài liệu & Vector Embeddings)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của đoạn văn bản tài liệu |
| `course_id` | `uuid` | NOT NULL, FK $\rightarrow$ `courses(id)` | Khóa học chứa tài liệu (Dùng để lọc cô lập dữ liệu bắt buộc) |
| `lesson_id` | `uuid` | NOT NULL, FK $\rightarrow$ `lessons(id)` | Bài học chứa tài liệu |
| `content_id` | `uuid` | NOT NULL, FK $\rightarrow$ `lesson_contents(id)`| Khối nội dung gốc (PDF, Docx, Slide, Text) |
| `chunk_index` | `int` | NOT NULL | Thứ tự của đoạn văn bản trong toàn bộ tài liệu |
| `chunk_text` | `text` | NOT NULL | Nội dung văn bản thô của đoạn (khoảng 500-1000 tokens) |
| `token_count` | `int` | NOT NULL | Số lượng token thực tế của đoạn văn bản |
| `embedding_vector`| `text` *(Postgres: `vector(1536)`)* | Nullable | Tọa độ vector biểu diễn ngữ nghĩa tạo bởi mô hình Embedding |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm hoàn tất bóc tách và nhúng vector |

*Index tối ưu:* 
* `HNSW Index` trên cột `embedding_vector` sử dụng toán tử khoảng cách `vector_cosine_ops`.
* Composite B-Tree Index trên `(course_id, lesson_id)` để đảm bảo truy vấn vector chỉ quét các tài liệu thuộc đúng khóa học của học viên.

#### Bảng `ai_conversations` (Phiên hội thoại hỏi đáp với AI)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của phiên hội thoại |
| `enrollment_id` | `uuid` | NOT NULL, FK $\rightarrow$ `enrollments(id)` | Phiên ghi danh của học viên mở hội thoại |
| `lesson_id` | `uuid` | Nullable, FK $\rightarrow$ `lessons(id)` | Bài học đang mở khi học viên đặt câu hỏi (Ngữ cảnh ưu tiên) |
| `title` | `varchar(255)` | Nullable | Tiêu đề tóm tắt chủ đề hội thoại do AI sinh |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm bắt đầu phiên hội thoại |

#### Bảng `ai_messages` (Chi tiết tin nhắn hội thoại, Đo lường Chi phí & Trích dẫn)
| Tên cột | Kiểu dữ liệu | Ràng buộc / Index | Mô tả chức năng & Ý nghĩa kỹ thuật |
| :--- | :--- | :--- | :--- |
| `id` | `uuid` | PK, Default: `gen_random_uuid()` | Khóa chính của tin nhắn |
| `conversation_id` | `uuid` | NOT NULL, FK $\rightarrow$ `ai_conversations(id)`| Phiên hội thoại chứa tin nhắn này |
| `role` | `varchar(20)` | NOT NULL | Vai trò phát ngôn (`'user'` hoặc `'assistant'`) |
| `content` | `text` | NOT NULL | Nội dung câu hỏi của học viên hoặc câu trả lời của AI |
| `cited_chunk_ids` | `jsonb` | Nullable | Mảng chứa các `id` của `ai_document_chunks` được mô hình trích dẫn làm căn cứ |
| `prompt_tokens` | `int` | Nullable | Số lượng token đầu vào (Dùng đo lường chi phí LLM Token) |
| `completion_tokens`| `int` | Nullable | Số lượng token mô hình sinh ra ở đầu ra |
| `latency_ms` | `int` | Nullable | Độ trễ xử lý toàn trình từ lúc gửi prompt đến khi nhận câu trả lời (ms) |
| `is_refused` | `boolean` | NOT NULL, Default: `false` | Cờ ghi nhận AI từ chối trả lời (do ngoài phạm vi hoặc thiếu bằng chứng) |
| `created_at` | `timestamptz` | NOT NULL, Default: `now()` | Thời điểm gửi tin nhắn |

*Index tối ưu:* Index trên `conversation_id` để tải nhanh lịch sử đoạn hội thoại khi học viên mở lại giao diện.