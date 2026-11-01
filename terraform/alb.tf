# =============================================================================
# Application Load Balancer (ALB) & Path-based Routing
# =============================================================================

resource "aws_lb" "main" {
  name               = "${local.name_prefix}-alb"
  internal           = false
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  drop_invalid_header_fields = true
  enable_deletion_protection = var.environment == "production"

  tags = {
    Name = "${local.name_prefix}-alb"
  }
}

# -----------------------------------------------------------------------------
# Target Groups (IP target type for AWS ECS Fargate)
# -----------------------------------------------------------------------------

# 1. Frontend SPA Target Group (port 8080)
resource "aws_lb_target_group" "frontend" {
  name        = "${local.name_prefix}-frontend-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/healthz"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${local.name_prefix}-frontend-tg"
  }
}

# 2. API Gateway Target Group (port 8080)
resource "aws_lb_target_group" "api_gateway" {
  name        = "${local.name_prefix}-gateway-tg"
  port        = 8080
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${local.name_prefix}-gateway-tg"
  }
}

# 3. Core LMS Service Target Group (port 8081)
resource "aws_lb_target_group" "core_lms" {
  name        = "${local.name_prefix}-core-lms-tg"
  port        = 8081
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${local.name_prefix}-core-lms-tg"
  }
}

# 4. Assessment Service Target Group (port 8082)
resource "aws_lb_target_group" "assessment" {
  name        = "${local.name_prefix}-assessment-tg"
  port        = 8082
  protocol    = "HTTP"
  vpc_id      = aws_vpc.main.id
  target_type = "ip"

  health_check {
    path                = "/actuator/health"
    matcher             = "200"
    interval            = 30
    timeout             = 5
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = {
    Name = "${local.name_prefix}-assessment-tg"
  }
}

# -----------------------------------------------------------------------------
# ALB Listener (Port 80 HTTP Default)
# -----------------------------------------------------------------------------
resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.main.arn
  port              = 80
  protocol          = "HTTP"

  # Default action forwards to Frontend SPA
  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.frontend.arn
  }
}

# -----------------------------------------------------------------------------
# Path-based Routing Rules
# -----------------------------------------------------------------------------

# Rule 1: Route Core LMS domain APIs to core-lms-service (:8081)
resource "aws_lb_listener_rule" "core_lms" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.core_lms.arn
  }

  condition {
    path_pattern {
      values = [
        "/api/v1/courses*",
        "/api/v1/lessons*",
        "/api/v1/enrollments*",
        "/api/v1/notifications*",
        "/api/v1/users*",
        "/api/v1/auth*"
      ]
    }
  }
}

# Rule 2: Route Assessment domain APIs to assessment-service (:8082)
resource "aws_lb_listener_rule" "assessment" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 20

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.assessment.arn
  }

  condition {
    path_pattern {
      values = [
        "/api/v1/quizzes*",
        "/api/v1/assignments*",
        "/api/v1/submissions*"
      ]
    }
  }
}

# Rule 3: Route remaining /api/* and /actuator/* to api-gateway (:8080)
resource "aws_lb_listener_rule" "api_gateway" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 30

  action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.api_gateway.arn
  }

  condition {
    path_pattern {
      values = ["/api/*", "/actuator/*"]
    }
  }
}
