variable "region" {
  type    = string
  default = "ap-southeast-1"
}

variable "environment" {
  type    = string
  default = "demo"
}

variable "instance_type" {
  description = "2 vCPU / 8 GB is the practical minimum for the 9 containers"
  type        = string
  default     = "t3.large"
}

variable "repo_url" {
  description = "Git URL the instance clones (use an HTTPS URL; add a token for a private repo)"
  type        = string
}

variable "repo_branch" {
  type    = string
  default = "build/deploy-prep"
}

variable "seed_demo_data" {
  description = "Load the sample teachers/students/courses (profile demo)"
  type        = bool
  default     = true
}

variable "ssh_cidr" {
  description = "CIDR allowed to SSH (22). Empty = no SSH at all (use SSM Session Manager)."
  type        = string
  default     = ""
}

variable "bucket_name" {
  description = "Globally-unique S3 bucket for lesson content and submissions. Empty = oep-<account>-<region>."
  type        = string
  default     = ""
}
