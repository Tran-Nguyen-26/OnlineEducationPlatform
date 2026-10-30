# =============================================================================
# AWS Secrets Manager (Credentials & Sensitive Application Config)
# =============================================================================

resource "random_password" "jwt_secret" {
  length  = 48
  special = false
}

# -----------------------------------------------------------------------------
# 1. Database Credentials Secret
# -----------------------------------------------------------------------------
resource "aws_secretsmanager_secret" "database" {
  name                    = "${var.project_name}/${var.environment}/database"
  description             = "Database credentials for ${local.name_prefix}"
  recovery_window_in_days = var.environment == "staging" ? 0 : 7

  tags = {
    Name = "${local.name_prefix}-database-secret"
  }
}

resource "aws_secretsmanager_secret_version" "database" {
  secret_id = aws_secretsmanager_secret.database.id
  secret_string = jsonencode({
    host        = aws_db_instance.main.address
    port        = aws_db_instance.main.port
    dbname      = var.db_name
    username    = var.db_username
    password    = random_password.db_password.result
    db_url      = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${var.db_name}"
    replica_url = "jdbc:postgresql://${aws_db_instance.main.address}:${aws_db_instance.main.port}/${var.db_name}"
  })
}

# -----------------------------------------------------------------------------
# 2. Application Secrets (JWT, Mail, Broker)
# -----------------------------------------------------------------------------
resource "aws_secretsmanager_secret" "app_secrets" {
  name                    = "${var.project_name}/${var.environment}/app-secrets"
  description             = "Application secrets for ${local.name_prefix} services"
  recovery_window_in_days = var.environment == "staging" ? 0 : 7

  tags = {
    Name = "${local.name_prefix}-app-secrets"
  }
}

resource "aws_secretsmanager_secret_version" "app_secrets" {
  secret_id = aws_secretsmanager_secret.app_secrets.id
  secret_string = jsonencode({
    jwt_secret    = random_password.jwt_secret.result
    mail_username = "notifications@oep.edu.vn"
    mail_password = "replace_with_prod_smtp_token"
  })
}
