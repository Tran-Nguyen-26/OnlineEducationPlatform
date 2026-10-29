variable "aws_region" {
  description = "AWS region for provisioning infrastructure"
  type        = string
  default     = "ap-southeast-1"
}

variable "environment" {
  description = "Target deployment environment (staging or production)"
  type        = string
  default     = "staging"
}

variable "project_name" {
  description = "Project identifier used in resource tagging and naming"
  type        = string
  default     = "oep"
}
