# =============================================================================
# Amazon ECS Fargate Cluster, Task Definitions, and Microservices
# =============================================================================

# -----------------------------------------------------------------------------
# Amazon ECR Repositories (5 Modules)
# -----------------------------------------------------------------------------
locals {
  services = {
    frontend = {
      name           = "oep-frontend"
      container_port = 8080
      target_group   = aws_lb_target_group.frontend.arn
      is_web         = true
      image_stub     = "nginxinc/nginx-unprivileged:alpine"
    }
    api_gateway = {
      name           = "oep-api-gateway"
      container_port = 8080
      target_group   = aws_lb_target_group.api_gateway.arn
      is_web         = true
      image_stub     = "public.ecr.aws/docker/library/busybox:latest"
    }
    core_lms = {
      name           = "oep-core-lms-service"
      container_port = 8081
      target_group   = aws_lb_target_group.core_lms.arn
      is_web         = true
      image_stub     = "public.ecr.aws/docker/library/busybox:latest"
    }
    assessment = {
      name           = "oep-assessment-service"
      container_port = 8082
      target_group   = aws_lb_target_group.assessment.arn
      is_web         = true
      image_stub     = "public.ecr.aws/docker/library/busybox:latest"
    }
    worker = {
      name           = "oep-worker-service"
      container_port = 8083
      target_group   = null
      is_web         = false
      image_stub     = "public.ecr.aws/docker/library/busybox:latest"
    }
  }
}

resource "aws_ecr_repository" "repos" {
  for_each             = local.services
  name                 = each.value.name
  image_tag_mutability = var.environment == "production" ? "IMMUTABLE" : "MUTABLE"
  force_delete         = var.environment == "staging"

  image_scanning_configuration {
    scan_on_push = true
  }

  tags = {
    Name = each.value.name
  }
}

# -----------------------------------------------------------------------------
# ECS Cluster
# -----------------------------------------------------------------------------
resource "aws_ecs_cluster" "main" {
  name = "${local.name_prefix}-cluster"

  setting {
    name  = "containerInsights"
    value = var.environment == "production" ? "enabled" : "disabled"
  }

  tags = {
    Name = "${local.name_prefix}-cluster"
  }
}

resource "aws_ecs_cluster_capacity_providers" "main" {
  cluster_name = aws_ecs_cluster.main.name

  capacity_providers = ["FARGATE", "FARGATE_SPOT"]

  default_capacity_provider_strategy {
    capacity_provider = var.use_fargate_spot ? "FARGATE_SPOT" : "FARGATE"
    weight            = 100
    base              = 1
  }
}

# -----------------------------------------------------------------------------
# CloudWatch Log Groups
# -----------------------------------------------------------------------------
resource "aws_cloudwatch_log_group" "services" {
  for_each          = local.services
  name              = "/ecs/${local.name_prefix}/${each.key}"
  retention_in_days = var.log_retention_days

  tags = {
    Name = "${local.name_prefix}-${each.key}-logs"
  }
}

# -----------------------------------------------------------------------------
# ECS Task Definitions
# -----------------------------------------------------------------------------
resource "aws_ecs_task_definition" "services" {
  for_each                 = local.services
  family                   = "${local.name_prefix}-${each.key}"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = tostring(var.ecs_fargate_cpu)
  memory                   = tostring(var.ecs_fargate_memory)
  execution_role_arn       = aws_iam_role.ecs_execution_role.arn
  task_role_arn            = aws_iam_role.ecs_task_role.arn

  container_definitions = jsonencode([
    {
      name      = each.key
      image     = "${aws_ecr_repository.repos[each.key].repository_url}:staging-latest"
      essential = true

      portMappings = [
        {
          containerPort = each.value.container_port
          hostPort      = each.value.container_port
          protocol      = "tcp"
        }
      ]

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          "awslogs-group"         = aws_cloudwatch_log_group.services[each.key].name
          "awslogs-region"        = var.aws_region
          "awslogs-stream-prefix" = "ecs"
        }
      }

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = var.environment },
        { name = "AWS_REGION", value = var.aws_region },
        { name = "AWS_SQS_QUEUE_NAME", value = aws_sqs_queue.notification_queue.name },
        { name = "AWS_SQS_DLQ_NAME", value = aws_sqs_queue.notification_dlq.name },
        { name = "S3_BUCKET_NAME", value = aws_s3_bucket.assets.id },
        { name = "CLOUDFRONT_DOMAIN", value = aws_cloudfront_distribution.assets.domain_name }
      ]

      secrets = [
        {
          name      = "DB_URL"
          valueFrom = "${aws_secretsmanager_secret.database.arn}:db_url::"
        },
        {
          name      = "DB_USER"
          valueFrom = "${aws_secretsmanager_secret.database.arn}:username::"
        },
        {
          name      = "DB_PASSWORD"
          valueFrom = "${aws_secretsmanager_secret.database.arn}:password::"
        },
        {
          name      = "JWT_SECRET"
          valueFrom = "${aws_secretsmanager_secret.app_secrets.arn}:jwt_secret::"
        }
      ]
    }
  ])

  tags = {
    Name = "${local.name_prefix}-${each.key}-task"
  }
}

# -----------------------------------------------------------------------------
# ECS Services (with Deployment Circuit Breaker & Rollback)
# -----------------------------------------------------------------------------
resource "aws_ecs_service" "services" {
  for_each        = local.services
  name            = each.value.name
  cluster         = aws_ecs_cluster.main.id
  task_definition = aws_ecs_task_definition.services[each.key].arn
  desired_count   = var.ecs_desired_count

  capacity_provider_strategy {
    capacity_provider = var.use_fargate_spot ? "FARGATE_SPOT" : "FARGATE"
    weight            = 100
    base              = 1
  }

  network_configuration {
    subnets          = aws_subnet.private_app[*].id
    security_groups  = [aws_security_group.ecs_tasks.id]
    assign_public_ip = false
  }

  # Automated 1-step rollback on deployment health check failure
  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  deployment_minimum_healthy_percent = 50
  deployment_maximum_percent         = 200

  dynamic "load_balancer" {
    for_each = each.value.is_web ? [1] : []
    content {
      target_group_arn = each.value.target_group
      container_name   = each.key
      container_port   = each.value.container_port
    }
  }

  # Allow CI/CD deployments to update task definitions without causing state drift
  lifecycle {
    ignore_changes = [task_definition]
  }

  depends_on = [
    aws_lb_listener.http,
    aws_iam_role_policy_attachment.ecs_execution
  ]

  tags = {
    Name = each.value.name
  }
}
