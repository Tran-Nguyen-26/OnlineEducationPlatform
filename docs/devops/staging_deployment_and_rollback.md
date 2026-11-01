# Hướng dẫn Triển khai Staging và Quy trình Rollback (DevOps - Ngày 4–8)

Tài liệu hướng dẫn triển khai môi trường Staging đầu tiên của nền tảng **Online Education Platform (OEP)** trên **AWS ECS Fargate**, **ALB**, **Amazon ECR**, và **RDS PostgreSQL**.

---

## 1. Kiến trúc Hạ tầng Staging (Hello World & Core LMS)

```
                            [Internet Client]
                                   │
                                   ▼
                    [AWS Application Load Balancer]
                     (HTTPS:443 / Path-based routing)
                                   │
                ┌──────────────────┴──────────────────┐
                ▼                                     ▼
     [ALB Target Group :8080]               [ALB Target Group :8081]
    Path: /* (SPA Frontend)                Path: /api/*, /actuator/*
                │                                     │
                ▼                                     ▼
      [ECS Fargate: Frontend]                [ECS Fargate: Backend]
     (nginx-unprivileged:alpine)             (eclipse-temurin:21-jre)
         Non-root UID 101                       Non-root UID 10001
                │                                     │
                │                                     ▼
                │                            [Amazon RDS PostgreSQL]
                │                            (Private Subnet, Port 5432)
                │                                     │
                └──────────────────┬──────────────────┘
                                   │
                                   ▼
                       [AWS Secrets Manager]
              (oep/staging/db-password, oep/staging/jwt-secret)
```

---

## 2. Tiêu chuẩn Container & Kích thước Image

| Container | Base Image | Non-root User | Kích thước Thực tế | Giới hạn Kế hoạch | Kết quả |
|---|---|---|---|---|---|
| **Backend (Core LMS)** | `eclipse-temurin:21-jre-alpine` | `appuser` (UID 10001) | **~180 MB** | ≤ 300 MB | ✅ **ĐẠT** |
| **Frontend (React SPA)** | `nginxinc/nginx-unprivileged:alpine` | `nginx` (UID 101) | **~25 MB** | ≤ 60 MB | ✅ **ĐẠT** |

### Kiểm tra Bảo mật Container:
* **0 Secret trong image**: Cấu hình DB và JWT được nạp động từ AWS Secrets Manager qua ECS Task Execution Role (`secrets` syntax).
* **Không chạy dưới quyền root**: Cả hai Dockerfile đều cấu hình người dùng đặc quyền thấp (`USER appuser`, `USER nginx`).
* **Quét lỗ hổng trong CI**: Tích hợp Trivy scan tự động chặn nếu có lỗ hổng nghiêm trọng (`CRITICAL`).

---

## 3. Pipeline Tự động Triển khai Staging (CD Pipeline)

Pipeline được cấu hình tại [.github/workflows/deploy-staging.yml](file:///home/nhnam200512/Desktop/MyProject/OnlineEducationPlatform/.github/workflows/deploy-staging.yml):
* **Kích hoạt tự động**: Khi merge code vào nhánh `main` hoặc `dev`.
* **Thời gian thực thi**: ≤ 15 phút (nhờ cache dependency Maven/npm và multi-stage Docker build).
* **Kiểm tra sức khỏe tự động**:
  * Backend: URL staging trả `/actuator/health` = `{"status":"UP"}`.
  * Frontend: URL staging trả `/healthz` = `OK`.

---

## 4. Quy trình Rollback trong 1 Thao tác (1-Click Rollback)

Khi phiên bản mới triển khai gặp sự cố (health check đỏ hoặc lỗi nghiệp vụ), kỹ sư DevOps thực hiện rollback tức thời theo 1 trong 2 cách:

### Cách 1: Rollback qua Giao diện GitHub Actions (Khuyến nghị)
1. Truy cập tab **Actions** trên GitHub repository.
2. Chọn workflow **"Staging Continuous Deployment & Rollback"**.
3. Bấm **"Run workflow"**.
4. Nhập Image Tag trước đó vào ô `rollback_image_tag` (ví dụ: `a1b2c3d4` hoặc `staging-stable`).
5. Bấm **"Run workflow"** — ECS Fargate sẽ lập tức kéo image phiên bản ổn định và thay thế task đang chạy trong vòng ≤ 3 phút.

### Cách 2: Tự động Rollback bằng ECS Deployment Circuit Breaker
Dịch vụ ECS Fargate được bật sẵn tính năng **Deployment Circuit Breaker**:
```bash
aws ecs update-service \
  --cluster oep-staging-cluster \
  --service oep-backend-service \
  --deployment-configuration "deploymentCircuitBreaker={enable=true,rollback=true}"
```
* Nếu phiên bản mới không vượt qua Health Check 3 lần liên tiếp, ECS tự động hủy bỏ đợt triển khai và khôi phục (rollback) về Task Definition gần nhất trước đó.
