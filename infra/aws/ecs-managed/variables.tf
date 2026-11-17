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

variable "bucket_name" {
  type    = string
  default = ""
}
