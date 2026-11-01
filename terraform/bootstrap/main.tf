# =============================================================================
# Bootstrap Configuration for Terraform Remote State & Distributed Locking
# =============================================================================

terraform {
  required_version = ">= 1.5.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "oep"
      ManagedBy = "Terraform-Bootstrap"
    }
  }
}

variable "aws_region" {
  description = "AWS region for remote state infrastructure"
  type        = string
  default     = "ap-southeast-1"
}

data "aws_caller_identity" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
}

# -----------------------------------------------------------------------------
# 1. S3 Bucket for Remote State Storage
# -----------------------------------------------------------------------------
resource "aws_s3_bucket" "tf_state" {
  bucket        = "oep-terraform-state-${local.account_id}"
  force_destroy = false

  tags = {
    Name = "oep-terraform-state"
  }
}

resource "aws_s3_bucket_versioning" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_public_access_block" "tf_state" {
  bucket = aws_s3_bucket.tf_state.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

# -----------------------------------------------------------------------------
# 2. DynamoDB Table for Distributed State Locking
# -----------------------------------------------------------------------------
resource "aws_dynamodb_table" "tf_locks" {
  name         = "oep-terraform-locks"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "LockID"

  attribute {
    name = "LockID"
    type = "S"
  }

  point_in_time_recovery {
    enabled = true
  }

  tags = {
    Name = "oep-terraform-locks"
  }
}

output "state_bucket_name" {
  description = "Name of S3 bucket for remote state"
  value       = aws_s3_bucket.tf_state.id
}

output "dynamodb_table_name" {
  description = "Name of DynamoDB table for state locking"
  value       = aws_dynamodb_table.tf_locks.id
}
