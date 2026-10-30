# =============================================================================
# Security Groups (Principle of Least Privilege & Strict Network Isolation)
# =============================================================================

# -----------------------------------------------------------------------------
# 1. ALB Security Group (Public Edge)
# -----------------------------------------------------------------------------
resource "aws_security_group" "alb" {
  name        = "${local.name_prefix}-alb-sg"
  description = "Controls public inbound traffic to Application Load Balancer"
  vpc_id      = aws_vpc.main.id

  ingress {
    description = "HTTP inbound from internet"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  ingress {
    description = "HTTPS inbound from internet"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Outbound strictly scoped to private ECS tasks in application subnets"
    from_port   = 8080
    to_port     = 8083
    protocol    = "tcp"
    cidr_blocks = var.private_app_subnet_cidrs
  }

  tags = {
    Name = "${local.name_prefix}-alb-sg"
  }
}

# -----------------------------------------------------------------------------
# 2. ECS Tasks Security Group (Application Tier in Private Subnets)
# -----------------------------------------------------------------------------
resource "aws_security_group" "ecs_tasks" {
  name        = "${local.name_prefix}-ecs-tasks-sg"
  description = "Allows ingress only from ALB and inter-service container calls"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Inbound from ALB to frontend container"
    from_port       = 8080
    to_port         = 8080
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description     = "Inbound from ALB to Core LMS service"
    from_port       = 8081
    to_port         = 8081
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description     = "Inbound from ALB to Assessment service"
    from_port       = 8082
    to_port         = 8082
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description     = "Inbound from ALB to Worker service health endpoint"
    from_port       = 8083
    to_port         = 8083
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  ingress {
    description = "Internal inter-service communication between ECS tasks"
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    self        = true
  }

  egress {
    description = "Outbound HTTPS for AWS APIs (ECR, SQS, Secrets Manager, CloudWatch via NAT GW)"
    from_port   = 443
    to_port     = 443
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    description = "Outbound PostgreSQL strictly to private DB subnets"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = var.private_db_subnet_cidrs
  }

  tags = {
    Name = "${local.name_prefix}-ecs-tasks-sg"
  }
}

# -----------------------------------------------------------------------------
# 3. RDS PostgreSQL Security Group (Database Tier - Zero Public Access & Zero Egress)
# -----------------------------------------------------------------------------
resource "aws_security_group" "rds" {
  name        = "${local.name_prefix}-rds-sg"
  description = "Restricts PostgreSQL port 5432 to ECS task containers only"
  vpc_id      = aws_vpc.main.id

  ingress {
    description     = "Allow PostgreSQL access strictly from ECS tasks SG"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.ecs_tasks.id]
  }

  # No egress rule needed: PostgreSQL is purely reactive and stateful SG handles return packets

  tags = {
    Name = "${local.name_prefix}-rds-sg"
  }
}
