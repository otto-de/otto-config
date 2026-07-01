#!/usr/bin/env bash
# Push an EventBridge-shaped change event into the moto-hosted SQS queue
# so Otto Config's SQS listener picks it up and forces an immediate
# refresh of the given source.
#
# Without this you have to wait for the safety-net scheduler to fire
# (every 5 minutes for AppConfig / Secrets / SSM in the Spring demo).
#
# Usage:
#   ./notify-change.sh appconfig [properties|toggles]
#   ./notify-change.sh secrets
#   ./notify-change.sh ssm     /search/develop/otto-config/config/some_ssm_value
#
# Requires: aws CLI on your PATH and the local docker-compose stack up.
set -euo pipefail

# Load .env so AWS_ENDPOINT_URL / creds are set even in a fresh shell.
if [ -f "$(dirname "$0")/.env" ]; then
  # shellcheck disable=SC1091
  . "$(dirname "$0")/.env"
fi

: "${AWS_ENDPOINT_URL:=http://localhost:5000}"
: "${AWS_REGION:=eu-central-1}"
QUEUE_URL="${AWS_ENDPOINT_URL}/123456789012/otto-config-config-changes"

kind="${1:-}"
arg="${2:-}"

case "$kind" in
  appconfig)
    profile="${arg:-properties}"
    body=$(cat <<EOF
{
  "source": "aws.appconfig",
  "detail-type": "AWS AppConfig Deployment Status",
  "detail": {
    "application-id": "otto-config",
    "application-name": "otto-config",
    "environment-id": "local",
    "environment-name": "local",
    "configuration-profile-id": "${profile}",
    "configuration-profile-name": "${profile}"
  }
}
EOF
)
    ;;
  secrets)
    body=$(cat <<EOF
{
  "source": "aws.secretsmanager",
  "detail-type": "AWS API Call via CloudTrail",
  "detail": {
    "requestParameters": { "secretId": "otto-config" }
  }
}
EOF
)
    ;;
  ssm)
    name="${arg:-/search/develop/otto-config/config/some_ssm_value}"
    body=$(cat <<EOF
{
  "source": "aws.ssm",
  "detail-type": "Parameter Store Change",
  "detail": { "name": "${name}", "operation": "Update" }
}
EOF
)
    ;;
  *)
    echo "usage: $0 {appconfig [properties|toggles] | secrets | ssm [name]}" >&2
    exit 2
    ;;
esac

aws --endpoint-url "${AWS_ENDPOINT_URL}" sqs send-message \
    --queue-url "${QUEUE_URL}" \
    --message-body "${body}" \
    --output text >/dev/null

echo "sent ${kind} change event to ${QUEUE_URL}"
