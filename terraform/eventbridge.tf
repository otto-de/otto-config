# ---------------------------------------------------------------------------
# Event-driven configuration change notifications
#
# When var.change_notification_enabled is true this module creates:
#   1. An SQS queue (+ dead-letter queue) as the central event sink
#   2. An SQS queue policy that allows EventBridge to send messages
#   3. Three EventBridge rules:
#        a. AppConfig  – fires on every deployment completion
#        b. SecretsManager – fires on PutSecretValue / UpdateSecret (via CloudTrail)
#        c. SSM Parameter Store – fires on any parameter change under the service path
#   4. An IAM policy (attached to the consumer role) granting the minimum SQS
#      permissions needed by AwsChangeEventListener
#
# All resources are conditional on var.change_notification_enabled so that the
# module remains backward-compatible for callers that do not opt in.
# ---------------------------------------------------------------------------

locals {
  queue_name = "${var.service}-config-changes"
  dlq_name   = "${var.service}-config-changes-dlq"

  # Suppress plan noise when the feature is disabled
  create_notifications = var.change_notification_enabled ? 1 : 0
}

# ---------------------------------------------------------------------------
# Dead-letter queue
# ---------------------------------------------------------------------------
resource "aws_sqs_queue" "change_notifications_dlq" {
  count = local.create_notifications

  name                      = local.dlq_name
  message_retention_seconds = 1209600 # 14 days

  tags = {
    Service = var.service
    Purpose = "event-driven-refresh-dlq"
  }
}

# ---------------------------------------------------------------------------
# Main notification queue
# ---------------------------------------------------------------------------
resource "aws_sqs_queue" "change_notifications" {
  count = local.create_notifications

  name                       = local.queue_name
  visibility_timeout_seconds = 60
  message_retention_seconds  = 86400 # 1 day

  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.change_notifications_dlq[0].arn
    maxReceiveCount     = 5
  })

  tags = {
    Service = var.service
    Purpose = "event-driven-refresh"
  }
}

# ---------------------------------------------------------------------------
# SQS queue policy – allow EventBridge to publish to the queue
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "eventbridge_to_sqs" {
  count = local.create_notifications

  statement {
    sid    = "AllowEventBridgePublish"
    effect = "Allow"

    principals {
      type        = "Service"
      identifiers = ["events.amazonaws.com"]
    }

    actions   = ["sqs:SendMessage"]
    resources = [aws_sqs_queue.change_notifications[0].arn]

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values = [
        aws_cloudwatch_event_rule.appconfig_deployment[0].arn,
        aws_cloudwatch_event_rule.secretsmanager_change[0].arn,
        aws_cloudwatch_event_rule.ssm_parameter_change[0].arn,
      ]
    }
  }
}

resource "aws_sqs_queue_policy" "change_notifications" {
  count = local.create_notifications

  queue_url = aws_sqs_queue.change_notifications[0].url
  policy    = data.aws_iam_policy_document.eventbridge_to_sqs[0].json
}

# ---------------------------------------------------------------------------
# EventBridge rule 1 – AppConfig deployment completion
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_event_rule" "appconfig_deployment" {
  count = local.create_notifications

  name        = "${var.service}-appconfig-deployment"
  description = "Captures AppConfig deployment completions for ${var.service}"

  event_pattern = jsonencode({
    source      = ["aws.appconfig"]
    # eu-central-1 (and several other regions) does not emit native
    # "AWS AppConfig Deployment Status" events. Instead it delivers
    # CloudTrail management events with detail-type
    # "AWS API Call via CloudTrail". We match StartDeployment so that
    # the event fires as soon as a deployment is triggered (deployments
    # with 0-minute duration complete synchronously within the same call).
    detail-type = ["AWS API Call via CloudTrail"]
    detail = {
      eventSource = ["appconfig.amazonaws.com"]
      eventName   = ["StartDeployment"]
      requestParameters = {
        applicationId = [aws_appconfig_application.app_config.id]
      }
    }
  })

  tags = {
    Service = var.service
  }
}

resource "aws_cloudwatch_event_target" "appconfig_deployment_to_sqs" {
  count = local.create_notifications

  rule      = aws_cloudwatch_event_rule.appconfig_deployment[0].name
  target_id = "AppConfigToSqs"
  arn       = aws_sqs_queue.change_notifications[0].arn
}

# ---------------------------------------------------------------------------
# EventBridge rule 2 – Secrets Manager secret rotation / value update
#                       (delivered via CloudTrail – requires a trail to be
#                        active in the account/region)
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_event_rule" "secretsmanager_change" {
  count = local.create_notifications

  name        = "${var.service}-secretsmanager-change"
  description = "Captures Secrets Manager write events for ${var.service}"

  event_pattern = jsonencode({
    source      = ["aws.secretsmanager"]
    detail-type = ["AWS API Call via CloudTrail"]
    detail = {
      eventSource = ["secretsmanager.amazonaws.com"]
      eventName   = ["PutSecretValue", "UpdateSecret", "RotateSecret"]
    }
  })

  tags = {
    Service = var.service
  }
}

resource "aws_cloudwatch_event_target" "secretsmanager_change_to_sqs" {
  count = local.create_notifications

  rule      = aws_cloudwatch_event_rule.secretsmanager_change[0].name
  target_id = "SecretsManagerToSqs"
  arn       = aws_sqs_queue.change_notifications[0].arn
}

# ---------------------------------------------------------------------------
# EventBridge rule 3 – SSM Parameter Store changes under the service path
# ---------------------------------------------------------------------------
resource "aws_cloudwatch_event_rule" "ssm_parameter_change" {
  count = local.create_notifications

  name        = "${var.service}-ssm-parameter-change"
  description = "Captures SSM Parameter Store changes for ${var.service}"

  event_pattern = jsonencode({
    source      = ["aws.ssm"]
    detail-type = ["Parameter Store Change"]
  })

  tags = {
    Service = var.service
  }
}

resource "aws_cloudwatch_event_target" "ssm_parameter_change_to_sqs" {
  count = local.create_notifications

  rule      = aws_cloudwatch_event_rule.ssm_parameter_change[0].name
  target_id = "SsmToSqs"
  arn       = aws_sqs_queue.change_notifications[0].arn
}

# ---------------------------------------------------------------------------
# IAM policy for the application's runtime role
# ---------------------------------------------------------------------------
data "aws_iam_policy_document" "sqs_consumer" {
  count = local.create_notifications

  statement {
    sid    = "SqsConsumer"
    effect = "Allow"
    actions = [
      "sqs:ReceiveMessage",
      "sqs:DeleteMessage",
      "sqs:DeleteMessageBatch",
      "sqs:GetQueueAttributes",
    ]
    resources = [aws_sqs_queue.change_notifications[0].arn]
  }
}

resource "aws_iam_policy" "sqs_consumer" {
  count = local.create_notifications

  name        = "${var.service}-sqs-consumer"
  description = "Allows the ${var.service} application to poll the configuration-change notification queue"
  policy      = data.aws_iam_policy_document.sqs_consumer[0].json

  tags = {
    Service = var.service
  }
}

resource "aws_iam_role_policy_attachment" "sqs_consumer" {
  count = local.create_notifications

  role       = split("/", var.change_notification_consumer_role_arn)[1]
  policy_arn = aws_iam_policy.sqs_consumer[0].arn
}
