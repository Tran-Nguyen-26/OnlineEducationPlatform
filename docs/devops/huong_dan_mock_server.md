# Hướng dẫn Khởi chạy và Sử dụng Mock Server cho Frontend (1 Trang)

Tài liệu ngắn gọn giúp thành viên Frontend khởi chạy Mock API Server cho **100% module đã có OpenAPI (M1–M6)** và kiểm thử các trạng thái giao diện trong **≤ 10 phút**.

---

## 1. Khởi chạy Mock Server

Di chuyển vào thư mục `frontend` và chạy một lệnh duy nhất:

```bash
cd frontend
npm run mock
```

Mock server tự động:
* Tổng hợp toàn bộ hợp đồng OpenAPI từ thư mục `openapi/` (`m3-enrollment.yaml`, `m5-assignment.yaml`, `m6-notification.yaml`...).
* Lắng nghe tại: **`http://localhost:4010`**
* **Watch Mode**: Tự động phát hiện và cập nhật dữ liệu phản hồi ngay khi có bất kỳ file OpenAPI nào thay đổi (không cần khởi động lại).

---

## 2. Kết nối từ Frontend Application

Trong mã nguồn React (hoặc file `.env.local`), cấu hình URL gọi API trỏ về Mock Server:

```env
VITE_API_BASE_URL=http://localhost:4010
```

---

## 3. Kiểm thử Trạng thái Lỗi Giao diện (Error States)

Prism Mock Server hỗ trợ giả lập phản hồi thành công (200/201) và các mã lỗi mẫu (**400, 403, 500**) thông qua **HTTP Header** hoặc **Query Parameter**:

| Trạng thái cần test | Cách 1: Dùng Header HTTP | Cách 2: Dùng Query Param | Ví dụ URL kiểm tra bằng cURL |
|---|---|---|---|
| **Thành công (200/201)** | *(Mặc định)* | *(Mặc định)* | `curl http://localhost:4010/notifications` |
| **400 Bad Request** | `Prefer: code=400` | `?__status=400` | `curl -H "Prefer: code=400" http://localhost:4010/notifications` |
| **403 Forbidden** | `Prefer: code=403` | `?__status=403` | `curl -H "Prefer: code=403" http://localhost:4010/notifications` |
| **500 Server Error** | `Prefer: code=500` | `?__status=500` | `curl -H "Prefer: code=500" http://localhost:4010/notifications` |

### Ví dụ kiểm thử trong React Query:
```typescript
// Thêm header Prefer: code=403 khi muốn kiểm tra màn hình 403 Access Denied
const response = await fetch('http://localhost:4010/notifications', {
  headers: {
    'Prefer': 'code=403'
  }
});
```

---

## 4. Xử lý sự cố thường gặp (Troubleshooting)

1. **Cổng 4010 đã bị chiếm dụng**:
   Chạy với biến môi trường đổi cổng:
   ```bash
   MOCK_PORT=4011 npm run mock
   ```
2. **Cập nhật spec OpenAPI mới**:
   Khi team Backend thêm hoặc cập nhật file `.yaml` trong thư mục `openapi/`, terminal đang chạy `npm run mock` sẽ tự động phát hiện và thông báo `[Watch Mode] OpenAPI file changed` trong vòng 1 giây.
