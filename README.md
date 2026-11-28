# OnlineEducationPlatform (OEP)

Nền tảng học trực tuyến và luyện thi tích hợp AI (Project 8 — Cloud Application Development): React 19 + Vite (`frontend/`), Spring Boot (`backend/`: `api-gateway`, `core-lms-service`, `assessment-service`, `worker-service`), Python/FastAPI RAG (`ai-tutor/`), và hạ tầng AWS Terraform (`infra/aws/`).

```text
docs/       Tài liệu yêu cầu nghiệp vụ chuẩn — BRD_Project8_OEP_v1.4.md (Single Source of Truth)
frontend/   Giao diện React SPA (Vite, TanStack Query, Tailwind v4, hls.js) — Dockerfile + nginx
backend/    Các service Spring Boot, docker-compose (dev & prod-like), k6/chaos, monitoring, tài liệu kỹ thuật
ai-tutor/   Dịch vụ AI Tutor RAG (Python/FastAPI, pgvector/Pinecone, 15 ADRs, 102 pytest)
infra/aws/  Terraform AWS: ec2-demo (demo Free Plan) và ecs-managed (kiến trúc production)
```

## Tài liệu chính (đã đồng bộ theo BRD v1.4)

* **Nghiệp vụ & Kiến trúc tổng thể:** [docs/BRD_Project8_OEP_v1.4.md](docs/BRD_Project8_OEP_v1.4.md) · [backend/BUSINESS.md](backend/BUSINESS.md)
* **Trạng thái triển khai & Đối chiếu học phần Cloud:** [backend/STATUS.md](backend/STATUS.md) · [backend/CLOUD_ALIGNMENT.md](backend/CLOUD_ALIGNMENT.md) · [backend/LOAD_TEST_REPORT.md](backend/LOAD_TEST_REPORT.md)
* **Hướng dẫn phát triển & chạy local:** [backend/CLAUDE.md](backend/CLAUDE.md) · [backend/README.md](backend/README.md) · [ai-tutor/README.md](ai-tutor/README.md) · [ai-tutor/TESTING_GUIDE.md](ai-tutor/TESTING_GUIDE.md)
* **Triển khai & Vận hành:** [backend/DEPLOYMENT.md](backend/DEPLOYMENT.md) · [backend/OPERATIONS.md](backend/OPERATIONS.md) · [infra/aws/README.md](infra/aws/README.md)
