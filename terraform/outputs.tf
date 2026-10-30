# =============================================================================
# Infrastructure Outputs
# =============================================================================

output "vpc_id" {
  description = "ID of the VPC"
  value       = aws_vpc.main.id
}

output "public_subnet_ids" {
  description = "IDs of the public subnets"
  value       = aws_subnet.public[*].id
}

output "private_app_subnet_ids" {
  description = "IDs of the private application subnets"
  value       = aws_subnet.private_app[*].id
}

output "private_db_subnet_ids" {
  description = "IDs of the private database subnets"
  value       = aws_subnet.private_db[*].id
}

# -----------------------------------------------------------------------------
# Edge & Networking Outputs
# -----------------------------------------------------------------------------
output "alb_dns_name" {
  description = "Public DNS name of the Application Load Balancer"
  value       = aws_lb.main.dns_name
}

output "alb_zone_id" {
  description = "Canonical hosted zone ID of the Application Load Balancer"
  value       = aws_lb.main.zone_id
}

output "cloudfront_domain_name" {
  description = "Domain name of the CloudFront distribution for CDN assets"
  value       = aws_cloudfront_distribution.assets.domain_name
}

output "cloudfront_distribution_id" {
  description = "ID of the CloudFront distribution"
  value       = aws_cloudfront_distribution.assets.id
}

# -----------------------------------------------------------------------------
# Storage & Messaging Outputs
# -----------------------------------------------------------------------------
output "s3_assets_bucket_name" {
  description = "Name of the S3 bucket for course media and documents"
  value       = aws_s3_bucket.assets.id
}

output "s3_assets_bucket_arn" {
  description = "ARN of the S3 assets bucket"
  value       = aws_s3_bucket.assets.arn
}

output "sqs_notification_queue_url" {
  description = "URL of the primary SQS notification queue"
  value       = aws_sqs_queue.notification_queue.url
}

output "sqs_notification_queue_arn" {
  description = "ARN of the primary SQS notification queue"
  value       = aws_sqs_queue.notification_queue.arn
}

output "sqs_notification_dlq_url" {
  description = "URL of the SQS Dead Letter Queue (DLQ)"
  value       = aws_sqs_queue.notification_dlq.url
}

output "sqs_notification_dlq_arn" {
  description = "ARN of the SQS Dead Letter Queue (DLQ)"
  value       = aws_sqs_queue.notification_dlq.arn
}

# -----------------------------------------------------------------------------
# Database Outputs
# -----------------------------------------------------------------------------
output "rds_endpoint" {
  description = "Connection endpoint for the RDS PostgreSQL database"
  value       = aws_db_instance.main.endpoint
}

output "rds_address" {
  description = "Hostname of the RDS PostgreSQL database"
  value       = aws_db_instance.main.address
}

output "rds_port" {
  description = "Port of the RDS PostgreSQL database"
  value       = aws_db_instance.main.port
}

output "rds_database_name" {
  description = "Database name on the RDS instance"
  value       = aws_db_instance.main.db_name
}

# -----------------------------------------------------------------------------
# Secrets & Security Outputs
# -----------------------------------------------------------------------------
output "secrets_manager_db_secret_arn" {
  description = "ARN of Secrets Manager secret for DB credentials"
  value       = aws_secretsmanager_secret.database.arn
}

output "secrets_manager_app_secret_arn" {
  description = "ARN of Secrets Manager secret for application secrets"
  value       = aws_secretsmanager_secret.app_secrets.arn
}

output "github_oidc_role_arn" {
  description = "ARN of IAM Role for GitHub Actions CI/CD OIDC authentication (set as AWS_OIDC_ROLE_ARN secret)"
  value       = aws_iam_role.github_deployer.arn
}

# -----------------------------------------------------------------------------
# ECS Outputs
# -----------------------------------------------------------------------------
output "ecs_cluster_name" {
  description = "Name of the ECS cluster"
  value       = aws_ecs_cluster.main.name
}

output "ecs_cluster_arn" {
  description = "ARN of the ECS cluster"
  value       = aws_ecs_cluster.main.arn
}

output "ecr_repository_urls" {
  description = "Map of ECR repository URLs for each microservice"
  value       = { for k, v in aws_ecr_repository.repos : k => v.repository_url }
}
