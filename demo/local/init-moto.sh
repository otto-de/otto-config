#!/usr/bin/env bash
# Seeds the moto server with the AWS resources the demo services expect:
#   - Secrets Manager secret 'otto-config'
#   - SSM parameters under /search/develop/otto-config
#   - SQS queue 'otto-config-config-changes'
#
# AppConfig is *not* seeded here — moto does not implement the
# AppConfigData data plane. The appconfigdata-stub container serves the
# hosted configuration content instead.
#
# Idempotent: re-running is safe (deletes and re-creates the secret /
# parameters / queue).
set -euo pipefail

SERVICE="otto-config"
SSM_PREFIX="/search/develop/otto-config"
QUEUE_NAME="otto-config-config-changes"

aws() {
  command aws --endpoint-url "${AWS_ENDPOINT_URL}" "$@"
}

echo ">>> Waiting for moto to accept API calls..."
for i in $(seq 1 30); do
  if aws sts get-caller-identity >/dev/null 2>&1; then break; fi
  sleep 1
done

########################################
# Secrets Manager
########################################
echo ">>> Seeding Secrets Manager secret '${SERVICE}'"
aws secretsmanager delete-secret --secret-id "${SERVICE}" --force-delete-without-recovery >/dev/null 2>&1 || true
SECRET_ARN=$(aws secretsmanager create-secret \
  --name "${SERVICE}" \
  --secret-string '{"some_secret":"some very secret value","some_other_secret":"some other secret value"}' \
  --query 'ARN' --output text)
echo "    secret arn = ${SECRET_ARN}"

########################################
# SSM Parameter Store
########################################
echo ">>> Seeding SSM parameters under ${SSM_PREFIX}"
aws ssm put-parameter --name "${SSM_PREFIX}/config/some_ssm_value" --type String --value "hello-from-ssm" --overwrite >/dev/null
aws ssm put-parameter --name "${SSM_PREFIX}/config/another_ssm_value" --type String --value "another-hello" --overwrite >/dev/null

########################################
# SQS change-notification queue
########################################
echo ">>> Seeding SQS queue '${QUEUE_NAME}'"
QUEUE_URL=$(aws sqs create-queue --queue-name "${QUEUE_NAME}" --query 'QueueUrl' --output text)
echo "    queue url = ${QUEUE_URL}"

echo ">>> moto bootstrap done."
