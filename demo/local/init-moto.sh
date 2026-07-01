#!/usr/bin/env bash
# Seeds the moto server with the AWS resources the demo services expect:
#   - Secrets Manager secret 'otto-config'
#   - SSM parameters under /search/develop/otto-config
#   - SQS queue 'otto-config-config-changes'
#   - S3 bucket 'otto-config-feature-toggles' populated with the marker
#     files under /s3-toggles (bind-mounted from demo/local/s3-toggles)
#
# AppConfig is *not* seeded here — moto does not implement the
# AppConfigData data plane. The appconfigdata-stub container serves the
# hosted configuration content instead.
#
# Idempotent: re-running is safe (deletes and re-creates the secret /
# parameters / queue / bucket).
set -euo pipefail

SERVICE="otto-config"
SSM_PREFIX="/search/develop/otto-config"
QUEUE_NAME="otto-config-config-changes"
S3_TOGGLES_BUCKET="otto-config-feature-toggles"
S3_TOGGLES_PREFIX="feature-toggles"
S3_TOGGLES_DIR="${S3_TOGGLES_DIR:-/s3-toggles}"

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

########################################
# S3 feature-toggles bucket (aws.s3.toggles)
#
# The S3TogglesSource looks at object *names* only: on.<name> / off.<name>
# under ${S3_TOGGLES_PREFIX}/ maps to enabled/disabled. Any content is
# ignored. Mirrors demo/terraform/s3_toggles.tf for the real AWS setup.
########################################
echo ">>> Seeding S3 bucket '${S3_TOGGLES_BUCKET}' with feature-toggle markers"
# Empty and delete first so re-runs don't fail on existing objects.
aws s3 rm "s3://${S3_TOGGLES_BUCKET}" --recursive >/dev/null 2>&1 || true
aws s3api delete-bucket --bucket "${S3_TOGGLES_BUCKET}" >/dev/null 2>&1 || true
aws s3api create-bucket \
  --bucket "${S3_TOGGLES_BUCKET}" \
  --create-bucket-configuration LocationConstraint="${AWS_DEFAULT_REGION}" >/dev/null

if [ -d "${S3_TOGGLES_DIR}" ]; then
  shopt -s nullglob
  for marker in "${S3_TOGGLES_DIR}"/on.* "${S3_TOGGLES_DIR}"/off.*; do
    name="$(basename "${marker}")"
    key="${S3_TOGGLES_PREFIX}/${name}"
    aws s3api put-object \
      --bucket "${S3_TOGGLES_BUCKET}" \
      --key "${key}" \
      --body "${marker}" >/dev/null
    echo "    uploaded s3://${S3_TOGGLES_BUCKET}/${key}"
  done
  shopt -u nullglob
else
  echo "    WARNING: ${S3_TOGGLES_DIR} not mounted, no marker files uploaded"
fi

echo ">>> moto bootstrap done."
