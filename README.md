# OnlineEducationPlatform (OEP)

Nền tảng giáo dục trực tuyến: React 19 + Vite (frontend) và Spring Boot (backend: gateway, core-lms, assessment, worker).

```
frontend/   giao diện (Vite, TanStack Query, Tailwind v4) — Dockerfile + nginx để đóng gói
backend/    các service Spring Boot, docker-compose (dev và prod-like), scripts, monitoring, tài liệu
infra/aws/  Terraform: ec2-demo (demo rẻ) và ecs-managed (kiến trúc production)
```

Bắt đầu: `backend/README.md` (chạy dev và prod-like), `backend/DEPLOYMENT.md` (triển khai), `backend/OPERATIONS.md` (vận hành), `infra/aws/README.md` (AWS).
