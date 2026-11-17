locals {
  rds_url         = "jdbc:postgresql://${aws_db_instance.primary.address}:5432/oep?sslmode=require"
  rds_replica_url = "jdbc:postgresql://${var.create_read_replica ? aws_db_instance.replica[0].address : aws_db_instance.primary.address}:5432/oep?sslmode=require"
  rabbit_host     = replace(replace(aws_mq_broker.rabbit.instances[0].endpoints[0], "amqps://", ""), ":5671", "")
  public_origin   = "https://${aws_cloudfront_distribution.site.domain_name}"
  s3_origin       = "https://${aws_s3_bucket.content.bucket}.s3.${var.region}.amazonaws.com"
  secret_arn      = aws_secretsmanager_secret.app.arn

  base_env = {
    SPRING_PROFILES_ACTIVE = "prod"
    AWS_REGION             = var.region
    TRACE_SAMPLE_RATE      = "0.1"
  }
  db_env = {
    DB_URL          = local.rds_url
    DB_USER         = "oep"
    DB_REPLICA_URL  = local.rds_replica_url
    DB_REPLICA_USER = "oep"
  }
  rabbit_env = {
    RABBIT_HOST = local.rabbit_host
    RABBIT_PORT = "5671"
    RABBIT_USER = "oep"
    RABBIT_SSL  = "true"
  }
  storage_env = {
    STORAGE_BUCKET = aws_s3_bucket.content.bucket
  }
  sm = { for k in ["db_password", "rabbit_password", "admin_password", "demo_password", "jwt_private_key"] : k => "${local.secret_arn}:${k}::" }

  services = {
    core-lms-service = {
      port = 8081, cpu = 512, memory = 1024, java = true, autoscale = false
      env = merge(local.base_env, local.db_env, local.rabbit_env, local.storage_env, {
        REDIS_HOST             = aws_elasticache_cluster.redis.cache_nodes[0].address
        ADMIN_SEED_EMAIL       = "admin@oep.local"
        SPRING_PROFILES_ACTIVE = var.seed_demo_data ? "prod,demo" : "prod"
        STORAGE_CORS_ORIGINS   = local.public_origin
      })
      secrets = {
        DB_PASSWORD         = local.sm.db_password
        DB_REPLICA_PASSWORD = local.sm.db_password
        RABBIT_PASSWORD     = local.sm.rabbit_password
        ADMIN_SEED_PASSWORD = local.sm.admin_password
        OEP_DEMO_PASSWORD   = local.sm.demo_password
        JWT_PRIVATE_KEY_PEM = local.sm.jwt_private_key
      }
    }
    assessment-service = {
      port = 8082, cpu = 512, memory = 1024, java = true, autoscale = true
      env = merge(local.base_env, local.db_env, local.rabbit_env, local.storage_env, {
        JWK_SET_URI = "http://core-lms-service.${local.name}.local:8081/.well-known/jwks.json"
      })
      secrets = {
        DB_PASSWORD         = local.sm.db_password
        DB_REPLICA_PASSWORD = local.sm.db_password
        RABBIT_PASSWORD     = local.sm.rabbit_password
      }
    }
    worker-service = {
      port    = 8083, cpu = 256, memory = 512, java = true, autoscale = false
      env     = merge(local.base_env, local.db_env, local.rabbit_env)
      secrets = { DB_PASSWORD = local.sm.db_password, RABBIT_PASSWORD = local.sm.rabbit_password }
    }
    api-gateway = {
      port = 8080, cpu = 512, memory = 1024, java = true, autoscale = true
      env = merge(local.base_env, {
        CORE_LMS_URL         = "http://core-lms-service.${local.name}.local:8081"
        ASSESSMENT_URL       = "http://assessment-service.${local.name}.local:8082"
        JWK_SET_URI          = "http://core-lms-service.${local.name}.local:8081/.well-known/jwks.json"
        REDIS_HOST           = aws_elasticache_cluster.redis.cache_nodes[0].address
        CORS_ALLOWED_ORIGINS = local.public_origin
        TRUSTED_PROXY_HOPS   = "3" # CloudFront -> ALB -> nginx
      })
      secrets = {}
    }
    frontend = {
      port = 80, cpu = 256, memory = 512, java = false, autoscale = false
      env = {
        GATEWAY_URL           = "http://api-gateway.${local.name}.local:8080"
        STORAGE_PUBLIC_ORIGIN = local.s3_origin
      }
      secrets = {}
    }
  }
}

# ------------------------------------------------------------------ images
resource "aws_ecr_repository" "svc" {
  for_each             = local.services
  name                 = "oep/${each.key}"
  image_tag_mutability = "MUTABLE"
  force_delete         = true
  image_scanning_configuration {
    scan_on_push = true
  }
}

resource "aws_ecr_lifecycle_policy" "svc" {
  for_each   = local.services
  repository = aws_ecr_repository.svc[each.key].name
  policy = jsonencode({
    rules = [{
      rulePriority = 1, description = "keep the last 10 images"
      selection    = { tagStatus = "any", countType = "imageCountMoreThan", countNumber = 10 }
      action       = { type = "expire" }
    }]
  })
}

# ------------------------------------------------------------------ IAM
data "aws_iam_policy_document" "ecs_assume" {
  statement {
    actions = ["sts:AssumeRole"]
    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "execution" {
  name               = "${local.name}-ecs-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_secrets" {
  role = aws_iam_role.execution.id
  name = "read-app-secret"
  policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = ["secretsmanager:GetSecretValue"], Resource = local.secret_arn }]
  })
}

resource "aws_iam_role" "task" {
  name               = "${local.name}-ecs-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_assume.json
}

resource "aws_iam_role_policy" "task_s3" {
  role = aws_iam_role.task.id
  name = "content-bucket"
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"], Resource = "${aws_s3_bucket.content.arn}/*" },
      { Effect = "Allow", Action = ["s3:ListBucket", "s3:GetBucketCors", "s3:PutBucketCors"], Resource = aws_s3_bucket.content.arn }
    ]
  })
}

# ------------------------------------------------------------------ cluster, discovery, logs
resource "aws_ecs_cluster" "main" {
  name = local.name
  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_service_discovery_private_dns_namespace" "local" {
  name = "${local.name}.local"
  vpc  = aws_vpc.main.id
}

resource "aws_service_discovery_service" "svc" {
  for_each = local.services
  name     = each.key
  dns_config {
    namespace_id = aws_service_discovery_private_dns_namespace.local.id
    dns_records {
      ttl  = 10
      type = "A"
    }
  }
  health_check_custom_config {
    failure_threshold = 1
  }
}

resource "aws_cloudwatch_log_group" "svc" {
  for_each          = local.services
  name              = "/oep/${var.environment}/${each.key}"
  retention_in_days = 14
}

# ------------------------------------------------------------------ tasks and services
resource "aws_ecs_task_definition" "svc" {
  for_each                 = local.services
  family                   = "${local.name}-${each.key}"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = each.value.cpu
  memory                   = each.value.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  container_definitions = jsonencode([{
    name         = each.key
    image        = "${aws_ecr_repository.svc[each.key].repository_url}:${var.image_tag}"
    essential    = true
    portMappings = [{ containerPort = each.value.port }]
    environment  = [for k, v in each.value.env : { name = k, value = v }]
    secrets      = [for k, v in each.value.secrets : { name = k, valueFrom = v }]
    healthCheck = {
      command     = each.value.java ? ["CMD-SHELL", "curl -fs http://127.0.0.1:9090/actuator/health/readiness | grep -q UP"] : ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1/healthz"]
      interval    = 15
      timeout     = 5
      retries     = 6
      startPeriod = 90
    }
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.svc[each.key].name
        awslogs-region        = var.region
        awslogs-stream-prefix = "ecs"
      }
    }
  }])
}

resource "aws_ecs_service" "svc" {
  for_each                           = local.services
  name                               = each.key
  cluster                            = aws_ecs_cluster.main.id
  task_definition                    = aws_ecs_task_definition.svc[each.key].arn
  desired_count                      = var.desired_count
  launch_type                        = "FARGATE"
  health_check_grace_period_seconds  = each.key == "frontend" ? 60 : null
  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  # a bad release rolls itself back instead of taking the site down
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = aws_subnet.private[*].id
    security_groups  = [aws_security_group.app.id]
    assign_public_ip = false
  }

  service_registries {
    registry_arn = aws_service_discovery_service.svc[each.key].arn
  }

  dynamic "load_balancer" {
    for_each = each.key == "frontend" ? [1] : []
    content {
      target_group_arn = aws_lb_target_group.web.arn
      container_name   = "frontend"
      container_port   = 80
    }
  }

  depends_on = [aws_lb_listener.http]
  lifecycle {
    ignore_changes = [desired_count] # autoscaling owns it
  }
}

resource "aws_appautoscaling_target" "svc" {
  for_each           = { for k, v in local.services : k => v if v.autoscale }
  service_namespace  = "ecs"
  scalable_dimension = "ecs:service:DesiredCount"
  resource_id        = "service/${aws_ecs_cluster.main.name}/${aws_ecs_service.svc[each.key].name}"
  min_capacity       = var.desired_count
  max_capacity       = 4
}

resource "aws_appautoscaling_policy" "cpu" {
  for_each           = aws_appautoscaling_target.svc
  name               = "${each.key}-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = each.value.service_namespace
  scalable_dimension = each.value.scalable_dimension
  resource_id        = each.value.resource_id
  target_tracking_scaling_policy_configuration {
    target_value       = 60
    scale_in_cooldown  = 300
    scale_out_cooldown = 60
    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}

# ------------------------------------------------------------------ load balancer + CDN
resource "aws_lb" "web" {
  name               = local.name
  load_balancer_type = "application"
  subnets            = aws_subnet.public[*].id
  security_groups    = [aws_security_group.alb.id]
  idle_timeout       = 60
}

resource "aws_lb_target_group" "web" {
  name        = "${local.name}-web"
  port        = 80
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"
  health_check {
    path                = "/healthz"
    matcher             = "200"
    interval            = 15
    healthy_threshold   = 2
    unhealthy_threshold = 4
  }
  deregistration_delay = 30
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.web.arn
  port              = 80
  protocol          = "HTTP"
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.web.arn
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

resource "aws_cloudfront_distribution" "site" {
  enabled         = true
  comment         = "OEP ${var.environment}"
  is_ipv6_enabled = true
  price_class     = "PriceClass_200"

  origin {
    origin_id   = "alb"
    domain_name = aws_lb.web.dns_name
    custom_origin_config {
      http_port              = 80
      https_port             = 443
      origin_protocol_policy = "http-only"
      origin_ssl_protocols   = ["TLSv1.2"]
      origin_read_timeout    = 60
    }
  }

  ordered_cache_behavior {
    path_pattern             = "/api/*"
    target_origin_id         = "alb"
    viewer_protocol_policy   = "redirect-to-https"
    allowed_methods          = ["GET", "HEAD", "OPTIONS", "PUT", "POST", "PATCH", "DELETE"]
    cached_methods           = ["GET", "HEAD"]
    cache_policy_id          = data.aws_cloudfront_cache_policy.disabled.id
    origin_request_policy_id = data.aws_cloudfront_origin_request_policy.all_viewer.id
    compress                 = true
  }

  ordered_cache_behavior {
    path_pattern           = "/assets/*"
    target_origin_id       = "alb"
    viewer_protocol_policy = "redirect-to-https"
    allowed_methods        = ["GET", "HEAD", "OPTIONS"]
    cached_methods         = ["GET", "HEAD"]
    cache_policy_id        = data.aws_cloudfront_cache_policy.optimized.id
    compress               = true
  }

  default_cache_behavior {
    target_origin_id         = "alb"
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
    cloudfront_default_certificate = true
  }
}
