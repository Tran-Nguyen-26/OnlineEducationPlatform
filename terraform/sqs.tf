# =============================================================================
# Amazon SQS Notification Queue & Dead Letter Queue (DLQ)
# =============================================================================

# -----------------------------------------------------------------------------
# Dead Letter Queue (DLQ)
# -----------------------------------------------------------------------------
resource "aws_sqs_queue" "notification_dlq" {
  name                      = "${local.name_prefix}-notification-dlq"
  message_retention_seconds = 1209600 # 14 days retention for inspection
  sqs_managed_sse_enabled   = true

  tags = {
    Name = "${local.name_prefix}-notification-dlq"
    Tier = "Messaging"
  }
}

# -----------------------------------------------------------------------------
# Main Notification Queue (Outbox Pattern -> Worker Consumer)
# -----------------------------------------------------------------------------
resource "aws_sqs_queue" "notification_queue" {
  name                       = "${local.name_prefix}-notification-queue"
  message_retention_seconds  = 345600 # 4 days
  visibility_timeout_seconds = 60
  sqs_managed_sse_enabled    = true

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.notification_dlq.arn
    maxReceiveCount     = 5 # Routed to DLQ after 5 failed retries
  })

  tags = {
    Name = "${local.name_prefix}-notification-queue"
    Tier = "Messaging"
  }
}
