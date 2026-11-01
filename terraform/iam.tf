# =============================================================================
# IAM Roles & Policies (Strict Least-Privilege, 0 Wildcard on App Data)
# =============================================================================

# -----------------------------------------------------------------------------
# 1. ECS Task Execution Role (Used by ECS Agent for Image Pull & Log Writing)
# -----------------------------------------------------------------------------
resource "aws_iam_role" "ecs_execution_role" {
  name = "${local.name_prefix}-ecs-execution-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${local.name_prefix}-ecs-execution-role"
  }
}

resource "aws_iam_policy" "ecs_execution_policy" {
  name        = "${local.name_prefix}-ecs-execution-policy"
  description = "Allows ECS agent to write logs and fetch secrets at container launch"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ECRTokenAuth"
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken"
        ]
        Resource = "*"
      },
      {
        Sid    = "ECRImagePull"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage"
        ]
        Resource = [
          "arn:aws:ecr:${var.aws_region}:${local.account_id}:repository/oep-*"
        ]
      },
      {
        Sid    = "CloudWatchLogsAccess"
        Effect = "Allow"
        Action = [
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ]
        Resource = [
          "arn:aws:logs:${var.aws_region}:${local.account_id}:log-group:/ecs/${local.name_prefix}/*:*"
        ]
      },
      {
        Sid    = "SecretsManagerAccess"
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          aws_secretsmanager_secret.database.arn,
          aws_secretsmanager_secret.app_secrets.arn
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_execution" {
  role       = aws_iam_role.ecs_execution_role.name
  policy_arn = aws_iam_policy.ecs_execution_policy.arn
}

# -----------------------------------------------------------------------------
# 2. ECS Task Role (Used by Application Code - Strict Least Privilege, 0 Wildcards)
# -----------------------------------------------------------------------------
resource "aws_iam_role" "ecs_task_role" {
  name = "${local.name_prefix}-ecs-task-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Action = "sts:AssumeRole"
        Effect = "Allow"
        Principal = {
          Service = "ecs-tasks.amazonaws.com"
        }
      }
    ]
  })

  tags = {
    Name = "${local.name_prefix}-ecs-task-role"
  }
}

resource "aws_iam_policy" "ecs_task_policy" {
  name        = "${local.name_prefix}-ecs-task-policy"
  description = "Application runtime access - 0 wildcard * on application resources"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "SQSNotificationAccess"
        Effect = "Allow"
        Action = [
          "sqs:SendMessage",
          "sqs:ReceiveMessage",
          "sqs:DeleteMessage",
          "sqs:GetQueueAttributes",
          "sqs:GetQueueUrl",
          "sqs:ChangeMessageVisibility"
        ]
        # Strictly restricted to project notification queue & DLQ
        Resource = [
          aws_sqs_queue.notification_queue.arn,
          aws_sqs_queue.notification_dlq.arn
        ]
      },
      {
        Sid    = "S3AssetsBucketList"
        Effect = "Allow"
        Action = [
          "s3:ListBucket"
        ]
        # Strictly scoped to project assets bucket
        Resource = [
          aws_s3_bucket.assets.arn
        ]
      },
      {
        Sid    = "S3AssetsObjectAccess"
        Effect = "Allow"
        Action = [
          "s3:GetObject",
          "s3:PutObject",
          "s3:DeleteObject"
        ]
        # Strictly scoped to project bucket object prefixes
        Resource = [
          "${aws_s3_bucket.assets.arn}/*"
        ]
      },
      {
        Sid    = "SecretsManagerRuntimeRead"
        Effect = "Allow"
        Action = [
          "secretsmanager:GetSecretValue"
        ]
        Resource = [
          aws_secretsmanager_secret.database.arn,
          aws_secretsmanager_secret.app_secrets.arn
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "ecs_task" {
  role       = aws_iam_role.ecs_task_role.name
  policy_arn = aws_iam_policy.ecs_task_policy.arn
}


# -----------------------------------------------------------------------------
# 3. GitHub Actions OIDC Deployer Role (For Staging & Prod Automated CI/CD)
# -----------------------------------------------------------------------------
resource "aws_iam_role" "github_deployer" {
  name = "${local.name_prefix}-github-deployer-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Federated = "arn:aws:iam::${local.account_id}:oidc-provider/token.actions.githubusercontent.com"
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringLike = {
            "token.actions.githubusercontent.com:sub" = "repo:${var.github_repo}:*"
          }
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          }
        }
      }
    ]
  })

  tags = {
    Name = "${local.name_prefix}-github-deployer-role"
  }
}

resource "aws_iam_policy" "github_deployer_policy" {
  name        = "${local.name_prefix}-github-deployer-policy"
  description = "Allows GitHub Actions CI/CD to push ECR images and trigger ECS deployments"

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ECRAuthToken"
        Effect = "Allow"
        Action = [
          "ecr:GetAuthorizationToken"
        ]
        Resource = "*"
      },
      {
        Sid    = "ECRPushImages"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:GetDownloadUrlForLayer",
          "ecr:BatchGetImage",
          "ecr:PutImage",
          "ecr:InitiateLayerUpload",
          "ecr:UploadLayerPart",
          "ecr:CompleteLayerUpload"
        ]
        Resource = [
          "arn:aws:ecr:${var.aws_region}:${local.account_id}:repository/oep-*"
        ]
      },
      {
        Sid    = "ECSDeployments"
        Effect = "Allow"
        Action = [
          "ecs:DescribeServices",
          "ecs:UpdateService",
          "ecs:DescribeTaskDefinition",
          "ecs:RegisterTaskDefinition"
        ]
        Resource = [
          "arn:aws:ecs:${var.aws_region}:${local.account_id}:service/${local.name_prefix}-cluster/*",
          "arn:aws:ecs:${var.aws_region}:${local.account_id}:task-definition/oep-*:*"
        ]
      },
      {
        Sid    = "PassRoleToECS"
        Effect = "Allow"
        Action = [
          "iam:PassRole"
        ]
        Resource = [
          aws_iam_role.ecs_execution_role.arn,
          aws_iam_role.ecs_task_role.arn
        ]
      }
    ]
  })
}

resource "aws_iam_role_policy_attachment" "github_deployer" {
  role       = aws_iam_role.github_deployer.name
  policy_arn = aws_iam_policy.github_deployer_policy.arn
}
