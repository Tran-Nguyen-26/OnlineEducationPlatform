# =============================================================================
# Production Environment Configuration (High Availability & Redundancy)
# =============================================================================

aws_region   = "ap-southeast-1"
environment  = "production"
project_name = "oep"

# Networking (Multi-AZ redundant NAT Gateways)
vpc_cidr                 = "10.1.0.0/16"
availability_zones       = ["ap-southeast-1a", "ap-southeast-1b"]
public_subnet_cidrs      = ["10.1.1.0/24", "10.1.2.0/24"]
private_app_subnet_cidrs = ["10.1.10.0/24", "10.1.11.0/24"]
private_db_subnet_cidrs  = ["10.1.20.0/24", "10.1.21.0/24"]
single_nat_gateway       = false

# RDS Database (PostgreSQL 16 + pgvector, db.t4g.medium, Multi-AZ)
rds_engine_version        = "16.4"
rds_instance_class        = "db.t4g.medium"
rds_allocated_storage     = 50
rds_max_allocated_storage = 300
rds_multi_az              = true
rds_backup_retention_days = 30
rds_skip_final_snapshot   = false
rds_deletion_protection   = true
db_name                   = "oep"
db_username               = "oep_admin"

# Storage
s3_force_destroy = false

# ECS Fargate (0.5 vCPU, 1024 MiB, On-Demand, min 2 tasks per service)
ecs_fargate_cpu    = 512
ecs_fargate_memory = 1024
ecs_desired_count  = 2
use_fargate_spot   = false
log_retention_days = 90

github_repo = "Tran-Nguyen-26/OnlineEducationPlatform"
