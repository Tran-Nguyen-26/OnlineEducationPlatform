variable "aws_region" {
  description = "AWS region for provisioning infrastructure"
  type        = string
  default     = "ap-southeast-1"
}

variable "environment" {
  description = "Target deployment environment (staging or production)"
  type        = string
  default     = "staging"
  validation {
    condition     = contains(["staging", "production"], var.environment)
    error_message = "Environment must be either 'staging' or 'production'."
  }
}

variable "project_name" {
  description = "Project identifier used in resource tagging and naming"
  type        = string
  default     = "oep"
}

# -----------------------------------------------------------------------------
# Networking (VPC & Subnets)
# -----------------------------------------------------------------------------
variable "vpc_cidr" {
  description = "CIDR block for the VPC"
  type        = string
  default     = "10.0.0.0/16"
}

variable "availability_zones" {
  description = "List of Availability Zones to deploy subnets into"
  type        = list(string)
  default     = ["ap-southeast-1a", "ap-southeast-1b"]
}

variable "public_subnet_cidrs" {
  description = "CIDR blocks for public subnets (ALB, NAT Gateway)"
  type        = list(string)
  default     = ["10.0.1.0/24", "10.0.2.0/24"]
}

variable "private_app_subnet_cidrs" {
  description = "CIDR blocks for private application subnets (ECS tasks)"
  type        = list(string)
  default     = ["10.0.10.0/24", "10.0.11.0/24"]
}

variable "private_db_subnet_cidrs" {
  description = "CIDR blocks for private database subnets (RDS PostgreSQL isolated)"
  type        = list(string)
  default     = ["10.0.20.0/24", "10.0.21.0/24"]
}

variable "single_nat_gateway" {
  description = "Whether to deploy a single NAT Gateway (true for staging cost saving, false for multi-AZ HA)"
  type        = bool
  default     = true
}

# -----------------------------------------------------------------------------
# Database (RDS PostgreSQL 16 + pgvector)
# -----------------------------------------------------------------------------
variable "rds_engine_version" {
  description = "PostgreSQL engine version"
  type        = string
  default     = "16.4"
}

variable "rds_instance_class" {
  description = "Instance type for RDS PostgreSQL instance (db.t4g.micro for staging, db.t4g.medium for prod)"
  type        = string
  default     = "db.t4g.micro"
}

variable "rds_allocated_storage" {
  description = "Initial storage in GB for RDS"
  type        = number
  default     = 20
}

variable "rds_max_allocated_storage" {
  description = "Max storage limit in GB for storage autoscaling"
  type        = number
  default     = 100
}

variable "rds_multi_az" {
  description = "Enable Multi-AZ deployment for high availability"
  type        = bool
  default     = false
}

variable "rds_backup_retention_days" {
  description = "Number of days to retain automated DB backups"
  type        = number
  default     = 7
}

variable "rds_skip_final_snapshot" {
  description = "Skip final snapshot on destroy (true for staging, false for production)"
  type        = bool
  default     = true
}

variable "rds_deletion_protection" {
  description = "Prevent accidental DB deletion (false for staging, true for production)"
  type        = bool
  default     = false
}

variable "db_name" {
  description = "Initial PostgreSQL database name"
  type        = string
  default     = "oep"
}

variable "db_username" {
  description = "Master username for PostgreSQL database"
  type        = string
  default     = "oep_admin"
}

# -----------------------------------------------------------------------------
# S3 & CloudFront
# -----------------------------------------------------------------------------
variable "s3_force_destroy" {
  description = "Allow destroying S3 bucket containing objects (true for staging teardown)"
  type        = bool
  default     = true
}

# -----------------------------------------------------------------------------
# ECS & Containers
# -----------------------------------------------------------------------------
variable "ecs_fargate_cpu" {
  description = "Fargate CPU units (256 = 0.25 vCPU, 512 = 0.5 vCPU)"
  type        = number
  default     = 256
}

variable "ecs_fargate_memory" {
  description = "Fargate Memory in MiB (512 = 0.5 GB, 1024 = 1 GB)"
  type        = number
  default     = 512
}

variable "ecs_desired_count" {
  description = "Desired number of running tasks per service"
  type        = number
  default     = 1
}

variable "use_fargate_spot" {
  description = "Use FARGATE_SPOT capacity provider for cost optimization in staging"
  type        = bool
  default     = true
}

variable "log_retention_days" {
  description = "CloudWatch log retention in days (14 for staging, 90 for production)"
  type        = number
  default     = 14
}

# -----------------------------------------------------------------------------
# Security & CI/CD OIDC
# -----------------------------------------------------------------------------
variable "github_repo" {
  description = "GitHub repository for OIDC trust relationship (owner/repo)"
  type        = string
  default     = "Tran-Nguyen-26/OnlineEducationPlatform"
}
