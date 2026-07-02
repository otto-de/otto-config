# ---------------------------------------------------------------------------
# S3 feature-toggle source (aws.s3.toggles)
#
# The S3TogglesSource encodes a toggle's state in the *object name* and never
# reads object content, so a toggle is simply an empty object named
# on.<name> / off.<name> under a prefix. Flipping a toggle is a rename
# (copy + delete) of the object — no service redeploy, only s3:ListBucket.
# ---------------------------------------------------------------------------
resource "aws_s3_bucket" "feature_toggles" {
  bucket = "${var.service}-feature-toggles"
}

locals {
  # toggle name => enabled; rendered into on.<name> / off.<name> object keys.
  feature_toggles = {
    s3_toggle1 = true
    s3_toggle2 = false
  }
}

resource "aws_s3_object" "feature_toggles" {
  for_each = local.feature_toggles

  bucket  = aws_s3_bucket.feature_toggles.id
  key     = "feature-toggles/${each.value ? "on" : "off"}.${each.key}"
  content = ""
}
