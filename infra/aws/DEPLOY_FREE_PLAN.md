# Triển khai demo trên AWS Free Plan (ec2-demo)

Tài liệu này mô tả cách bản demo đã được deploy thực tế bằng `infra/aws/ec2-demo`, dùng **credit của AWS Free Plan** (không trả tiền thật nếu không nâng cấp tài khoản).

## 1. Kết quả triển khai

| Mục | Giá trị |
|---|---|
| Region | `ap-southeast-2` (Sydney) — SCP của tài khoản chỉ cho phép region này |
| Máy chủ | 1 × EC2 `c7i-flex.large` (2 vCPU, 4 GB RAM) + 2 GB swap, ổ gp3 40 GB mã hóa |
| Địa chỉ | `https://<id>.cloudfront.net` (xem `terraform output url`) |
| Tài nguyên | 18 (EC2, Elastic IP, CloudFront, S3, IAM role, Security Group, 5 mật khẩu ngẫu nhiên) |
| Thời gian khởi động | ~10–15 phút sau `apply` (lần build Docker đầu tiên trên instance) |

## 2. Kiến trúc

```
Người dùng --HTTPS--> CloudFront --HTTP (prefix list của CloudFront)--> EC2 :80
                                                                        └─ Docker Compose
                                                                           frontend (nginx) -> api-gateway
                                                                             -> core-lms-service
                                                                             -> assessment-service
                                                                           worker-service (ffmpeg/HLS)
                                                                           postgres, redis, rabbitmq
Trình duyệt <--presigned URL--> S3 (video, bài nộp)      EC2 -> S3 qua IAM role (không dùng access key)
```

Không chạy: MinIO (thay bằng S3) và Prometheus/Grafana/Jaeger (profile `observability`, tắt).

## 3. Điều kiện trước khi deploy

1. Tài khoản AWS ở **Free Plan** còn credit (Billing → Credits). Bản demo này dùng $100 credit, hạn 165 ngày.
2. **IAM user** riêng cho Terraform (ví dụ `oep-deployer`, gắn `AdministratorAccess`), có access key. Không dùng root để deploy. Bật MFA cho root.
3. Công cụ trên máy deploy: `aws` CLI v2, `terraform` ≥ 1.5 (đã kiểm tra với 1.9.8).
4. Nhánh `build/deploy-prep` trên GitHub, **repo public** (hoặc dùng URL có token). Instance `git clone` nhánh này khi khởi động, nên mọi thay đổi cấu hình phải được push trước khi `apply`.

## 4. Các điều chỉnh riêng cho Free Plan

| Vấn đề | Cách xử lý |
|---|---|
| SCP chặn mọi region trừ Sydney (`UnauthorizedOperation ... explicit deny in a service control policy`) | `variable "region"` mặc định `ap-southeast-2` |
| Free Plan chỉ cho một số loại EC2 | Dùng `c7i-flex.large` (4 GB, free-tier eligible). `m7i-flex.large` (8 GB) là phương án dự phòng |
| RAM 4 GB, stack 4 service Java | Thêm swap 2 GB trong `user-data.sh.tftpl`; file `backend/docker-compose.demo.yml` hạ `mem_limit` của worker (768m), postgres (512m), rabbitmq (384m) |
| Chi phí | Không bật observability; không dùng NAT/RDS/MQ/ALB |

Kiểm tra loại máy trước khi apply (không tạo gì, không tốn credit):
```bash
aws ec2 run-instances --region ap-southeast-2 --dry-run --image-id <ami> \
  --instance-type c7i-flex.large --subnet-id <subnet> --count 1
# "DryRunOperation ... would have succeeded" = được phép
```

## 5. Các bước deploy

```bash
# 1) Đăng nhập CLI (tự nhập key khi được hỏi, không dán key vào chat/git)
aws configure              # region: ap-southeast-2, output: json
aws sts get-caller-identity

# 2) Đẩy cấu hình lên nhánh deploy
git push origin <nhánh-hiện-tại>:build/deploy-prep

# 3) Terraform
cd infra/aws/ec2-demo
terraform init
terraform plan  -var 'repo_url=https://github.com/<user>/<repo>.git'
terraform apply -var 'repo_url=https://github.com/<user>/<repo>.git'

# 4) Lấy thông tin (đợi ~10–15 phút để Docker build xong)
terraform output url
terraform output -raw admin_password      # admin@oep.local, phải đổi mật khẩu lần đầu
terraform output -raw demo_password       # mật khẩu các tài khoản mẫu
```

Theo dõi khởi động:
```bash
until [ "$(curl -s -o /dev/null -w '%{http_code}' "$(terraform output -raw url)/")" = 200 ]; do sleep 15; done
curl -s "$(terraform output -raw url)/actuator/health"      # 200
```
Trong lúc build, CloudFront trả `504` — bình thường.

## 6. Tài khoản demo

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Admin | `admin@oep.local` | `terraform output -raw admin_password` (đổi ở lần đăng nhập đầu) |
| Giảng viên | `gv.lan@oep.demo`, `gv.minh@oep.demo` | `terraform output -raw demo_password` |
| Học sinh | `hs01@oep.demo` … `hs08@oep.demo` | `terraform output -raw demo_password` |

Không ghi mật khẩu thật vào tài liệu hoặc git. Mật khẩu admin sau khi đổi không còn khớp với output của Terraform.

## 7. Kiểm tra trước buổi demo

- Đăng nhập admin, giảng viên và học sinh.
- Tải lên **video ngắn** (vài chục MB) và một file bài nộp (kiểm tra S3 CORS, presigned URL, HLS). Máy 4 GB chuyển đổi video chậm.
- Admin → *Vận hành*: outbox 0 failed, DLQ 0.
- Xem tài nguyên máy (qua SSM): `free -m`, `docker stats --no-stream`.

Vào shell không cần SSH:
```bash
aws ssm start-session --region ap-southeast-2 --target "$(terraform output -raw instance_id)"
sudo tail -f /var/log/oep-bootstrap.log
```
(cần cài *Session Manager plugin*).

## 8. Chi phí và an toàn credit

- Ước tính ~$2–3/ngày khi chạy liên tục (chưa kiểm chứng với bảng giá). Tạo **AWS Budget $5** có cảnh báo email.
- **Không nâng cấp lên Paid Plan** nếu chỉ muốn dùng credit: khi đó phần vượt credit tính tiền thật. Trên Free Plan, khi credit hết hoặc quá hạn tài khoản bị đóng/tạm dừng thay vì tính phí.
- Sau demo: **`terraform destroy`** (giữ nguyên file `terraform.tfstate` cho tới lúc đó) rồi xóa access key của `oep-deployer`.

```bash
terraform destroy -var 'repo_url=https://github.com/<user>/<repo>.git'
```

## 9. Sự cố thường gặp

| Triệu chứng | Nguyên nhân / cách xử lý |
|---|---|
| `explicit deny in a service control policy` | Đang dùng region khác Sydney. Đặt `region = ap-southeast-2` |
| Đăng nhập Console báo "Bad request" | Cookie `amazon.com` cũ trong trình duyệt. Xóa cookie hoặc dùng cửa sổ ẩn danh |
| CloudFront trả 504 sau `apply` | Docker đang build; đợi 10–15 phút |
| Container bị OOM / máy rất chậm | Kiểm tra `free -m`; nâng `instance_type` lên `m7i-flex.large` |
| Đổi `repo_url`, `user-data` hoặc `instance_type` | `user_data_replace_on_change = true`: instance tạo lại, **dữ liệu trong Postgres mất** và seed lại |

## 10. Giới hạn (nên nêu trong báo cáo)

- Một máy duy nhất, không có tính sẵn sàng cao; Postgres, Redis, RabbitMQ chạy trong container trên chính instance, không có backup tự động.
- CloudFront → EC2 đi qua HTTP trong AWS (origin bị khóa theo prefix list của CloudFront). HTTPS chỉ áp dụng giữa người dùng và CloudFront.
- Không có domain riêng (chứng chỉ ACM cho CloudFront phải ở `us-east-1`, SCP của tài khoản này có thể chặn), không có WAF, Terraform state lưu cục bộ.
- Kiến trúc production-shaped (ECS Fargate, RDS, ElastiCache, Amazon MQ) có ở `infra/aws/ecs-managed` nhưng **chưa được deploy** (~$150–200/tháng, vượt giới hạn Free Plan).
