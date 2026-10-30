## 📋 Thông Tin Pull Request

### 1. Phân loại Module & Luồng phụ trách
- **Module liên quan:**
  - [ ] M1: Authentication & User Profile
  - [ ] M2: Course & Content Management
  - [ ] M3: Enrollment & Progress Tracking
  - [ ] M4: Assessment, Quiz & Grading
  - [ ] M5: Notification, Realtime & Worker Service
  - [ ] M6: Payment & Billing
  - [ ] M10: AI Tutor & RAG Service (pgvector)
  - [ ] Cross-cutting / DevOps / Infrastructure / CI-CD
- **Luồng (Stream) phụ trách:**
  - [ ] L0: Team Lead / DevOps / Cloud Architecture
  - [ ] L1: Core LMS & Authentication
  - [ ] L2: Course & Content
  - [ ] L3: Progress & Analytics
  - [ ] L4: Quiz & Assessment
  - [ ] L5: Communication, Realtime Notification, Worker & AI
- **Tóm tắt thay đổi:**
  > Mô tả ngắn gọn (1-3 câu) mục tiêu của PR, vấn đề được giải quyết hoặc tính năng mới bổ sung.

---

### 2. Chi tiết các thay đổi chính
- 
- 

---

### 3. ✅ Definition of Done (DoD) Checklist
*Tuân thủ quy chuẩn chất lượng của dự án (Kế hoạch 8 tuần - Dòng 552: PR ≤ 400 dòng, ≥ 1 reviewer, DoD checklist)*:

- [ ] **Kích thước PR chuẩn:** Tổng số dòng thay đổi **dưới 400 dòng** (không tính lockfile/generated code) để đảm bảo chất lượng code review.
- [ ] **Unit Tests:** Đã viết hoặc cập nhật unit test cho toàn bộ logic mới; kiểm tra pass 100% tại máy local.
- [ ] **Kiểm tra mã nguồn cục bộ:**
  - **Backend:** `mvn clean test` pass sạch, sinh báo cáo JaCoCo coverage không lỗi.
  - **Frontend:** `npm run lint`, `npm run format:check`, `npm run type-check`, `npm run test:run` đều đạt kết quả xanh.
- [ ] **Bảo mật & Secrets:** Tuyệt đối không commit file `.env`, API key, token, private key hoặc credentials vào Git.
- [ ] **Flyway Migration (nếu có):** Tên file tuân thủ quy chuẩn `V<version>__<description>.sql`, câu lệnh DDL an toàn và idempotent.
- [ ] **API Contract:** Nếu sửa/thêm API Backend, đã cập nhật `openapi.yaml` và sinh lại TypeScript client cho Frontend.
- [ ] **Code Architecture:** Tuân thủ nguyên tắc Clean Architecture (tách biệt Domain, Application, Infrastructure, Presentation).

---

### 4. 🧪 Bằng chứng kiểm thử thủ công (Evidence)
*Cung cấp kết quả kiểm tra cục bộ (ảnh chụp màn hình giao diện, kết quả chạy lệnh test hoặc log kiểm thử)*:
- Kết quả test:

---

### 5. 👥 Reviewer Checklist (Dành cho người review)
- [ ] Kiểm tra kiến trúc, logic xử lý lỗi và tính toàn vẹn dữ liệu.
- [ ] Không có truy vấn N+1 hoặc rò rỉ bộ nhớ/kết nối tài nguyên.
- [ ] Tối thiểu 1 approval từ reviewer phụ trách module trước khi merge vào nhánh `dev`/`main`.
