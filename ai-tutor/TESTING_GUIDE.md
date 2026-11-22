# Thử AI Tutor trên máy này

## Kết quả đã xác nhận

Giao diện Student có tab **AI Tutor** trong trang học; Admin có mục **Tiến độ tài liệu AI Tutor** trong tab **Nội dung**. Ngày 22/11/2026, luồng thật qua gateway → core LMS → M7 → Jina → Groq đã trả lời câu hỏi về tài liệu Python, lưu lịch sử và trích dẫn đúng nguồn. Sau khi khởi động các dịch vụ dưới đây, mở <http://127.0.0.1:5173/>. Các tiến trình thử đã được dừng sau khi kiểm chứng theo yêu cầu của chủ dự án.

TEXT, PDF có văn bản, Markdown `.md` và JSON `.json` đã được tải lên qua M2/MinIO, lập chỉ mục bằng Jina và hỏi đáp bằng Groq qua gateway. Trình duyệt đã gửi một câu hỏi PDF từ giao diện Student và hiện câu trả lời cùng trích dẫn trang 1. Câu hỏi thiếu chứng cứ trả trạng thái từ chối; xóa nguồn khiến câu hỏi mới không còn trích dẫn nguồn cũ. Xem [tiến độ](PROGRESS.md).

## Thử sau khi khởi động

1. Mở <http://127.0.0.1:5173/> và đăng nhập `hs01@oep.demo` / `Demo@12345`.
2. Vào **Khóa học của tôi** → **Lập trình Python nhập môn** → **AI Tutor** ở đầu trang hoặc menu bên trái. Trạng thái phải là **Sẵn sàng trả lời**.
3. Hỏi `Python có những kiểu dữ liệu nào theo tài liệu khóa học?`. Kết quả đã kiểm tra với Groq nêu `int`, `float`, `str`, `bool` và có trích dẫn `[1]`. Chọn **Nguồn tham khảo** để mở bài học. Thử mở lại cuộc trò chuyện trong cột lịch sử.
4. Muốn xem trang Admin, đăng nhập `admin@oep.local` / `Admin@12345` ở cửa sổ riêng tư. **Lần đăng nhập đầu tiên bắt buộc đổi mật khẩu**; sau đó dùng mật khẩu mới. Vào khóa Python → **Nội dung** → **Tiến độ tài liệu AI Tutor**. Database demo trên máy này có bốn nguồn TEXT và file PDF/JSON đã `READY`; file Markdown thử đã được xóa để kiểm tra thu hồi nguồn.

Nếu URL không mở được, làm theo phần khởi động dưới đây. Đừng khởi động hai bản của cùng dịch vụ trên cùng cổng.

## Khởi động lại đúng cấu hình

Yêu cầu Docker Desktop, JDK 21, Maven, Python 3.11+, `uv`, Node.js và npm. Các lệnh sau dành cho **PowerShell**. Bắt đầu ở thư mục gốc `C:\Users\Admin\Desktop\OnlineEducationPlatform`.

### 1. Cấu hình cục bộ, chỉ làm một lần

- `ai-tutor/.env.local` được sao chép từ [mẫu M7](.env.example). Điền `AI_TUTOR_SERVICE_JWT_SECRET` dài ít nhất 32 ký tự, `JINA_API_KEY` và `GROQ_API_KEY`. File này đã được tạo trên máy hiện tại. Không đưa khóa vào Git hoặc chat.
- `backend/.env` bị Git bỏ qua và đã được cấu hình trên máy này: `OEP_POSTGRES_HOST_PORT=55432`, `DB_URL` và `DB_REPLICA_URL` trỏ tới database demo riêng `oep_ai_tutor_demo_1122`, `DB_USER`/`DB_REPLICA_USER` và mật khẩu thực tế của volume PostgreSQL. **Không sao chép đè file này bằng `backend/.env.example`**: file mẫu chỉ chứa placeholder. Database `oep` trong volume cũ thiếu bảng `attendance_records`, nên không dùng nó để thử trên máy hiện tại.
- Cổng `5432` và `5433` đã có tiến trình khác dùng. Compose lấy cổng PostgreSQL từ `OEP_POSTGRES_HOST_PORT` trong `backend/.env`; không sửa lại thành `5432` trên máy này. Mật khẩu volume cũ có thể khác mặc định trong Compose. Không xóa volume hoặc database cũ để xử lý lỗi khởi động.

### 2. Hạ tầng và thư viện Java

```powershell
Set-Location backend
docker compose --profile ai-tutor up -d postgres redis rabbitmq minio ai-postgres
$env:JAVA_TOOL_OPTIONS='-Duser.timezone=UTC'
mvn '-Dmaven.repo.local=E:\Caches\OnlineEducationPlatform\maven' -DskipTests install -q
Set-Location ..
```

Nếu MinIO vừa được tạo lại, chờ `http://127.0.0.1:9000/minio/health/live` trả HTTP 200. `docker compose --profile ai-tutor ps` phải cho thấy PostgreSQL, Redis, RabbitMQ, MinIO và ai-postgres đang chạy. Khi chuyển sang máy mới, tự tạo `backend/.env` từ file mẫu và điền cổng, database, tài khoản thật trước; chỉ database **mới** mới dùng mật khẩu khởi tạo từ Compose.

### 3. M7: bốn cửa sổ PowerShell trong `ai-tutor/`

Trong cửa sổ thứ nhất, chạy migration. Trong **mỗi** cửa sổ cho API, consumer và worker, chạy ba dòng thiết lập đầu tiên rồi một dòng tiến trình tương ứng:

```powershell
Set-Location ai-tutor
$env:UV_CACHE_DIR='E:\Caches\OnlineEducationPlatform\uv'
$env:UV_PROJECT_ENVIRONMENT='E:\Caches\OnlineEducationPlatform\ai-tutor-venv'
. .\scripts\load-local-env.ps1
uv run --locked python -m oep_ai_tutor.migrate
```

```powershell
# Cửa sổ API, sau khi nạp .env.local như trên:
uv run --locked uvicorn oep_ai_tutor.app:create_app --factory --host 127.0.0.1 --port 8000
# Cửa sổ consumer: uv run --locked python -m oep_ai_tutor.index_consumer
# Cửa sổ worker:   uv run --locked python -m oep_ai_tutor.index_worker
```

Chạy **mỗi tiến trình ở cửa sổ riêng**, không chạy ba dòng cuối nối tiếp trong một cửa sổ. `http://127.0.0.1:8000/healthz` phải trả `{"status":"ok"}`.

### 4. Backend: bốn cửa sổ PowerShell ở thư mục gốc

Script [run-local-backend.ps1](scripts/run-local-backend.ps1) chỉ nạp các biến cần thiết từ `backend/.env`, dùng service secret trong `ai-tutor/.env.local` cho core LMS và đặt timezone Java phù hợp. Chạy một dòng trong **mỗi cửa sổ riêng**; đợi core LMS in `Started CoreLmsApplication` trước khi thử đăng nhập.

```powershell
.\ai-tutor\scripts\run-local-backend.ps1 -Service core-lms-service
.\ai-tutor\scripts\run-local-backend.ps1 -Service assessment-service
.\ai-tutor\scripts\run-local-backend.ps1 -Service worker-service
.\ai-tutor\scripts\run-local-backend.ps1 -Service api-gateway
```

### 5. Frontend: một cửa sổ trong `frontend/`

```powershell
Set-Location frontend
$env:VITE_CACHE_DIR='E:\Caches\OnlineEducationPlatform\vite'
npm ci
.\node_modules\.bin\vite.cmd --configLoader runner --host 127.0.0.1
```

Mở <http://127.0.0.1:5173/>. Frontend gọi gateway ở cổng `8080`. Nếu cổng `5173`, `8080–8083` hoặc `8000` đã được dùng, kiểm tra tiến trình cũ và dừng đúng tiến trình của dự án trước khi mở thêm bản mới.

## Đưa tài liệu mới vào và hỏi đáp

1. Đăng nhập Admin, mở khóa **PUBLISHED** có Student đang `ACTIVE`, chọn tab **Nội dung**. Mở một bài học, thêm **Nội dung** loại **Văn bản**. Ví dụ nhập `Mã thử AI Tutor: cây sao xanh có 17 lá.`
2. Để thử file, thêm loại **Tài liệu PDF** (PDF có thể bôi đen văn bản) hoặc **File khác** (`.md`/`.json`), rồi **Chọn & tải file lên**. PDF tối đa 20 MiB; Markdown/JSON tối đa 5 MiB. PDF chỉ có ảnh được báo `OCR_REQUIRED`; OCR chưa hỗ trợ.
3. Mở **Tiến độ tài liệu AI Tutor**. Với nguồn demo cũ chưa có trong M7, bấm **Đồng bộ lại tài liệu**. Chờ `READY`; bảng tự làm mới sau 10 giây. Nếu `FAILED`, xem mã lỗi và log cửa sổ worker/consumer.
4. Đăng nhập Student có quyền khóa đó, mở **AI Tutor**, hỏi một chi tiết cụ thể từ tài liệu. Câu trả lời phải dẫn đúng nguồn; PDF có số trang, Markdown có heading, JSON có path nếu có. Chọn trích dẫn để mở bài học hoặc file.
5. Hỏi chi tiết không có trong tài liệu: AI Tutor phải từ chối khi thiếu chứng cứ. Xóa nguồn qua Admin rồi hỏi lại: câu trả lời mới không được trích dẫn nguồn đã xóa. Student khác không được đọc lịch sử của nhau.

AI Tutor bị chặn nếu khóa chưa mở, Student không có enrollment `ACTIVE`, bài học còn khóa theo chế độ tuần tự hoặc đang làm quiz trong cùng khóa. Giao diện ẩn tab trong khóa đã hoàn thành ở chế độ chỉ xem.

## Kiểm thử và xử lý lỗi thường gặp

- `FATAL: password authentication failed for user "oep"`: `backend/.env` chưa dùng mật khẩu thực của volume PostgreSQL hiện có. Mật khẩu trong `docker-compose.yml` chỉ áp dụng khi volume khởi tạo lần đầu.
- `Schema-validation: missing table ...`: database cũ có schema không đầy đủ; dùng database demo riêng mới với Flyway như máy này. Không xóa database cũ.
- `Only one usage of each socket address`: một tiến trình khác đang giữ cổng. Dùng `Get-NetTCPConnection -State Listen | Where-Object LocalPort -eq 8081` để tìm PID, rồi kiểm tra bằng `Get-Process -Id <PID>` trước khi dừng.
- `AI_INDEX_NOT_READY`: chờ worker, kiểm tra trạng thái Admin và bấm **Đồng bộ lại tài liệu** cho nội dung demo được seed trước khi có outbox.
- HTTP `403` khi Admin gọi gateway: tài khoản mặc định chưa đổi mật khẩu lần đầu. Hoàn tất màn hình đổi mật khẩu, đăng nhập lại bằng mật khẩu mới.
- Lệnh `uv` cần chạy trong `ai-tutor/` và nạp `.env.local` ở từng cửa sổ. Lệnh backend cần chạy qua script ở thư mục gốc. Không nạp `backend/.env.example` vào tiến trình.

Đã chạy lại: M7 **102 passed, 1 skipped**, Java core LMS + gateway **90 tests, 0 failures/errors**, Java core LMS + worker **87 tests, 0 failures/errors, 1 skip**, frontend `npm run build`; hỏi đáp Jina/Groq thật qua gateway và giao diện có trích dẫn. Bài tích hợp RabbitMQ trong bộ pytest cần `AI_TUTOR_TEST_RABBITMQ_URL`; bài migration cần `AI_TUTOR_TEST_DATABASE_URL` trỏ tới database pgvector **mới, riêng biệt**, không dùng database demo. Xem [README](README.md) và [danh sách công việc](IMPLEMENTATION_PLAN.md).
