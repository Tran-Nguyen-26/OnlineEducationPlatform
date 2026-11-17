variable "region" {
  type    = string
  default = "ap-southeast-1"
}

variable "environment" {
  type    = string
  default = "demo"
}

variable "image_tag" {
  description = "Tag of the images pushed to the ECR repositories (see README)"
  type        = string
  default     = "latest"
}

variable "db_instance_class" {
  description = "RDS size. Connection budget = (task replicas) x (primary pool 30 + replica pool 20); db.t4g.micro allows only ~100 connections"
  type        = string
  default     = "db.t4g.small"
}

variable "multi_az" {
  description = "RDS Multi-AZ standby (production-like availability, doubles the DB cost)"
  type        = bool
  default     = false
}

variable "create_read_replica" {
  description = "RDS read replica used by DB_REPLICA_URL (otherwise the primary serves reads too)"
  type        = bool
  default     = false
}

variable "desired_count" {
  description = "Tasks per service (api-gateway and assessment also autoscale up to 4)"
  type        = number
  default     = 1
}

variable "seed_demo_data" {
  description = "Load the sample teachers/students/courses (profile demo)"
  type        = bool
  default     = true
}

variable "bucket_name" {
  type    = string
  default = ""
}
