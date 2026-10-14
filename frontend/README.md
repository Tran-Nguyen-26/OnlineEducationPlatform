# OEP Frontend

React SPA cho **Online Education Platform**, chạy song song với backend (Spring Boot).

## Tech Stack

| Công nghệ                            | Vai trò                                  |
| ------------------------------------ | ---------------------------------------- |
| **Vite 8 + React 19 + TypeScript 6** | Core                                     |
| **Tailwind CSS v4**                  | Styling (via `@tailwindcss/vite` plugin) |
| **React Query v5**                   | Server state / data fetching             |
| **React Router v7**                  | Client-side routing                      |
| **openapi-fetch**                    | Type-safe HTTP client từ OpenAPI schema  |
| **openapi-typescript**               | Sinh TypeScript types từ OpenAPI spec    |
| **oxlint**                           | Linting (nhanh hơn ESLint ~50-100×)      |
| **Prettier**                         | Code formatting                          |

## Cấu trúc thư mục

```
frontend/
├── src/
│   ├── api/
│   │   ├── client.ts              # openapi-fetch client (auto-attach JWT)
│   │   └── generated/
│   │       └── schema.d.ts        # Auto-generated từ OpenAPI spec (gitignored)
│   ├── features/
│   │   ├── auth/                  # Đăng nhập, đăng ký
│   │   ├── courses/               # Danh sách & chi tiết khóa học
│   │   ├── enrollment/            # Khóa học của tôi + tiến độ
│   │   ├── assessment/            # Quiz & bài kiểm tra
│   │   └── dashboard/             # Tổng quan / stats
│   ├── layouts/
│   │   ├── RootLayout.tsx         # Sidebar + main content
│   │   └── AuthLayout.tsx         # Centered auth wrapper
│   ├── lib/
│   │   └── router.tsx             # React Router config (lazy-loaded routes)
│   ├── App.tsx                    # QueryClientProvider + RouterProvider
│   ├── main.tsx                   # Entry point
│   └── index.css                  # Tailwind + design tokens
```

## Bắt đầu

```bash
cd frontend
npm install
npm run dev          # http://localhost:5173
```

### Scripts

| Script                 | Mô tả                                     |
| ---------------------- | ----------------------------------------- |
| `npm run dev`          | Dev server với HMR                        |
| `npm run build`        | TypeScript check + Vite production build  |
| `npm run lint`         | oxlint (lỗi + warning đều bị chặn)        |
| `npm run lint:fix`     | oxlint auto-fix                           |
| `npm run format`       | Prettier format toàn bộ project           |
| `npm run format:check` | Prettier check (dùng trong CI)            |
| `npm run type-check`   | `tsc --noEmit`                            |
| `npm run api:gen`      | Sinh types từ backend đang chạy ở `:8080` |

### Sinh API types

Khi backend đang chạy:

```bash
npm run api:gen
# Output: src/api/generated/schema.d.ts
```

## Thêm feature mới

1. Tạo thư mục `src/features/<tên-module>/`
2. Tạo `<TênModule>Page.tsx` (default export)
3. Thêm route vào `src/lib/router.tsx`
4. Thêm nav link vào `src/layouts/RootLayout.tsx`

## Proxy API

Dev server proxy `/api/*` → `http://localhost:8080` (xem `vite.config.ts`).
