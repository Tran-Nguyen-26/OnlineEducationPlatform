resource "random_password" "db" {
  length  = 28
  special = false
}
resource "random_password" "rabbit" {
  length  = 28
  special = false
}
resource "random_password" "admin" {
  length  = 14
  special = false
}

# JWT signing key (RS256). It lives in Terraform state + Secrets Manager; rotate by changing it and keeping the old public key.
resource "tls_private_key" "jwt" {
  algorithm = "RSA"
  rsa_bits  = 2048
}

# ------------------------------------------------------------------ PostgreSQL (RDS)
resource "aws_db_subnet_group" "db" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_db_instance" "primary" {
  identifier                      = "${local.name}-pg"
  engine                          = "postgres"
  engine_version                  = "16"
  instance_class                  = "db.t4g.micro"
  allocated_storage               = 20
  max_allocated_storage           = 100
  storage_type                    = "gp3"
  storage_encrypted               = true
  db_name                         = "oep"
  username                        = "oep"
  password                        = random_password.db.result
  db_subnet_group_name            = aws_db_subnet_group.db.name
  vpc_security_group_ids          = [aws_security_group.data.id]
  multi_az                        = var.multi_az
  backup_retention_period         = 7
  deletion_protection             = false # demo: allow terraform destroy
  skip_final_snapshot             = true
  auto_minor_version_upgrade      = true
  performance_insights_enabled    = false
  enabled_cloudwatch_logs_exports = ["postgresql"]
}

resource "aws_db_instance" "replica" {
  count                  = var.create_read_replica ? 1 : 0
  identifier             = "${local.name}-pg-replica"
  replicate_source_db    = aws_db_instance.primary.identifier
  instance_class         = "db.t4g.micro"
  storage_encrypted      = true
  vpc_security_group_ids = [aws_security_group.data.id]
  skip_final_snapshot    = true
}

# ------------------------------------------------------------------ Redis (ElastiCache)
resource "aws_elasticache_subnet_group" "redis" {
  name       = local.name
  subnet_ids = aws_subnet.private[*].id
}

resource "aws_elasticache_cluster" "redis" {
  cluster_id         = "${local.name}-redis"
  engine             = "redis"
  node_type          = "cache.t4g.micro"
  num_cache_nodes    = 1
  port               = 6379
  subnet_group_name  = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.data.id]
}

# ------------------------------------------------------------------ RabbitMQ (Amazon MQ)
resource "aws_mq_broker" "rabbit" {
  broker_name                = local.name
  engine_type                = "RabbitMQ"
  engine_version             = "3.13"
  host_instance_type         = "mq.t3.micro"
  deployment_mode            = "SINGLE_INSTANCE"
  publicly_accessible        = false
  subnet_ids                 = [aws_subnet.private[0].id]
  security_groups            = [aws_security_group.data.id]
  auto_minor_version_upgrade = true
  user {
    username = "oep"
    password = random_password.rabbit.result
  }
}

# ------------------------------------------------------------------ object storage
resource "aws_s3_bucket" "content" {
  bucket        = local.bucket
  force_destroy = true
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

# ------------------------------------------------------------------ secrets
resource "aws_secretsmanager_secret" "app" {
  name                    = "${local.name}/app"
  recovery_window_in_days = 0
}

resource "aws_secretsmanager_secret_version" "app" {
  secret_id = aws_secretsmanager_secret.app.id
  secret_string = jsonencode({
    db_password     = random_password.db.result
    rabbit_password = random_password.rabbit.result
    admin_password  = "Adm1n-${random_password.admin.result}"
    jwt_private_key = tls_private_key.jwt.private_key_pem_pkcs8
  })
}
