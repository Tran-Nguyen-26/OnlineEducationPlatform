output "url" {
  description = "Public HTTPS address (CloudFront)"
  value       = "https://${aws_cloudfront_distribution.site.domain_name}"
}

output "ecr_repositories" {
  description = "Push the five images here (tag = var.image_tag)"
  value       = { for k, r in aws_ecr_repository.svc : k => r.repository_url }
}

output "admin_login" {
  value = "admin@oep.local"
}

output "admin_password" {
  value     = "Adm1n-${random_password.admin.result}"
  sensitive = true
}
