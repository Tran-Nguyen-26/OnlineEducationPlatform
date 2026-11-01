# =============================================================================
# Staging Environment Configuration (Cost-optimized & Fast Provisioning)
# =============================================================================

aws_region   = "ap-southeast-1"
environment  = "staging"
project_name = "oep"

# Networking (Single NAT Gateway saves ~$32/month)
vpc_cidr                 = "10.0.0.0/16"
availability_zones       = ["ap-southeast-1a", "ap-southeast-1b"]
public_subnet_cidrs      = ["10.0.1.0/24", "10.0.2.0/24"]
private_app_subnet_cidrs = ["10.0.10.0/24", "10.0.11.0/24"]
private_db_subnet_cidrs  = ["10.0.20.0/24", "10.0.21.0/24"]
single_nat_gateway       = true

# RDS Database (PostgreSQL 16 + pgvector, db.t4g.micro, Single-AZ)
rds_engine_version        = "16.4"
rds_instance_class        = "db.t4g.micro"
rds_allocated_storage     = 20
rds_max_allocated_storage = 100
rds_multi_az              = false
rds_backup_retention_days = 7
rds_skip_final_snapshot   = true
rds_deletion_protection   = false
db_name                   = "oep"
db_username               = "oep_admin"

# Storage
s3_force_destroy = true

# ECS Fargate (0.25 vCPU, 512 MiB, FARGATE_SPOT saves ~70%)
ecs_fargate_cpu    = 256
ecs_fargate_memory = 512
ecs_desired_count  = 1
use_fargate_spot   = true
log_retention_days = 14

github_repo = "Tran-Nguyen-26/OnlineEducationPlatform"
