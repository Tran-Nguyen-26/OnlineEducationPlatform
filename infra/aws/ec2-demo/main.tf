data "aws_caller_identity" "me" {}

data "aws_vpc" "default" {
  default = true
}

data "aws_subnets" "default" {
  filter {
    name   = "vpc-id"
    values = [data.aws_vpc.default.id]
  }
}

# Ubuntu 24.04 LTS (Canonical)
data "aws_ami" "ubuntu" {
  most_recent = true
  owners      = ["099720109477"]
  filter {
    name   = "name"
    values = ["ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*"]
  }
}

locals {
  bucket = var.bucket_name != "" ? var.bucket_name : "oep-${data.aws_caller_identity.me.account_id}-${var.region}"
}

# ------------------------------------------------------------------ secrets (generated, never committed)
resource "random_password" "db" {
  length  = 28
  special = false
}
resource "random_password" "redis" {
  length  = 28
  special = false
}
resource "random_password" "rabbit" {
  length  = 28
  special = false
}
resource "random_password" "demo" {
  length  = 12
  special = false
}
resource "random_password" "admin" {
  length  = 14
  special = false
}

# ------------------------------------------------------------------ object storage (S3)
resource "aws_s3_bucket" "content" {
  bucket        = local.bucket
  force_destroy = true # demo environment: terraform destroy must be able to empty it
}

resource "aws_s3_bucket_public_access_block" "content" {
  bucket                  = aws_s3_bucket.content.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "content" {
  bucket = aws_s3_bucket.content.id
  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_bucket_cors_configuration" "content" {
  bucket = aws_s3_bucket.content.id
  cors_rule {
    allowed_methods = ["GET", "PUT", "HEAD"]
    allowed_origins = ["https://${aws_cloudfront_distribution.site.domain_name}"]
    allowed_headers = ["*"]
    expose_headers  = ["ETag"]
    max_age_seconds = 3000
  }
}

# ------------------------------------------------------------------ IAM: the instance reaches S3 through its role (no keys)
resource "aws_iam_role" "instance" {
  name = "oep-${var.environment}-instance"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Principal = { Service = "ec2.amazonaws.com" }, Action = "sts:AssumeRole" }]
  })
}

resource "aws_iam_role_policy" "s3" {
  role = aws_iam_role.instance.id
  name = "content-bucket"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"], Resource = "${aws_s3_bucket.content.arn}/*" },
      { Effect = "Allow", Action = ["s3:ListBucket", "s3:GetBucketCors", "s3:PutBucketCors"], Resource = aws_s3_bucket.content.arn }
    ]
  })
}

# Session Manager: shell access without opening port 22
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_instance_profile" "instance" {
  name = "oep-${var.environment}-instance"
  role = aws_iam_role.instance.name
}

# ------------------------------------------------------------------ network
data "aws_ec2_managed_prefix_list" "cloudfront" {
  name = "com.amazonaws.global.cloudfront.origin-facing"
}

resource "aws_security_group" "app" {
  name        = "oep-${var.environment}-app"
  description = "HTTP only from CloudFront; optional SSH"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description     = "CloudFront origin-facing only"
    from_port       = 80
    to_port         = 80
    protocol        = "tcp"
    prefix_list_ids = [data.aws_ec2_managed_prefix_list.cloudfront.id]
  }

  dynamic "ingress" {
    for_each = var.ssh_cidr == "" ? [] : [var.ssh_cidr]
    content {
      description = "SSH"
      from_port   = 22
      to_port     = 22
      protocol    = "tcp"
      cidr_blocks = [ingress.value]
    }
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_eip" "app" {
  domain = "vpc"
}

# ------------------------------------------------------------------ CDN + HTTPS (CloudFront in front of the instance)
resource "aws_cloudfront_distribution" "site" {
  enabled         = true
  comment         = "OEP ${var.environment}"
  is_ipv6_enabled = true
  price_class     = "PriceClass_200"

  origin {
    origin_id   = "app"
    domain_name = aws_eip.app.public_dns
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
      origin_read_timeout    = 60
    }
  }

  # API: never cached, every header/cookie/query string reaches the gateway
  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "app"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
    compress                 = true
  }

  # fingerprinted build assets: cached at the edge
  ordered_cache_behavior {
    path_pattern           = "/assets/*"
    target_origin_id       = "app"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.optimized.id
    compress               = true
  }

  default_cache_behavior {
    target_origin_id         = "app"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
    compress                 = true
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true # https://dxxxx.cloudfront.net, no domain needed
  }
}

data "aws_cloudfront_cache_policy" "disabled" {
  name = "Managed-CachingDisabled"
}
data "aws_cloudfront_cache_policy" "optimized" {
  name = "Managed-CachingOptimized"
}
data "aws_cloudfront_origin_request_policy" "all_viewer" {
  name = "Managed-AllViewer"
}

# ------------------------------------------------------------------ the server
resource "aws_instance" "app" {
  ami                    = data.aws_ami.ubuntu.id
  instance_type          = var.instance_type
  subnet_id              = tolist(data.aws_subnets.default.ids)[0]
  vpc_security_group_ids = [aws_security_group.app.id]
  iam_instance_profile   = aws_iam_instance_profile.instance.name

  metadata_options {
    http_tokens = "required" # IMDSv2 only
  }
  root_block_device {
    volume_size = 40
    volume_type = "gp3"
    encrypted   = true
  }

  user_data_replace_on_change = true
  user_data = templatefile("${path.module}/user-data.sh.tftpl", {
    repo_url        = var.repo_url
    repo_branch     = var.repo_branch
    region          = var.region
    bucket          = aws_s3_bucket.content.bucket
    public_origin   = "https://${aws_cloudfront_distribution.site.domain_name}"
    s3_origin       = "https://${aws_s3_bucket.content.bucket}.s3.${var.region}.amazonaws.com"
    db_password     = random_password.db.result
    redis_password  = random_password.redis.result
    rabbit_password = random_password.rabbit.result
    admin_password  = "Adm1n-${random_password.admin.result}"
    core_profiles   = var.seed_demo_data ? "prod,demo" : "prod"
    demo_password   = "Demo-${random_password.demo.result}"
  })

  tags = { Name = "oep-${var.environment}" }
}

resource "aws_eip_association" "app" {
  instance_id   = aws_instance.app.id
  allocation_id = aws_eip.app.id
}
