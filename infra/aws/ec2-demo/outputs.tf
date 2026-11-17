output "url" {
  description = "Open this (HTTPS, served by CloudFront). Allow ~10 minutes after apply for the first image build."
  value       = "https://${aws_cloudfront_distribution.site.domain_name}"
}

output "admin_login" {
  description = "Initial admin account (forced to change the password on first login)"
  value       = "admin@oep.local"
}

output "admin_password" {
  value     = "Adm1n-${random_password.admin.result}"
  sensitive = true # terraform output -raw admin_password
}

output "demo_password" {
  description = "Password of the sample accounts (gv.lan@oep.demo, gv.minh@oep.demo, hs01..hs08@oep.demo) when seed_demo_data=true"
  value       = "Demo-${random_password.demo.result}"
  sensitive   = true
}

output "instance_id" {
  description = "Shell access without SSH: aws ssm start-session --target <id>; bootstrap log: /var/log/oep-bootstrap.log"
  value       = aws_instance.app.id
}

output "bucket" {
  value = aws_s3_bucket.content.bucket
}
