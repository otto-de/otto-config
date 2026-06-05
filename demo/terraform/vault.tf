
resource "random_uuid" "auth_client_id" {
  keepers = {
    timestamp = timestamp()
  }
}

resource "random_uuid" "auth_client_secret" {
  keepers = {
    timestamp = timestamp()
  }
}

resource "vault_kv_secret_v2" "basic_auth" {
  mount               = "cftsearch"
  name                = "service/${var.service}/${var.environment}/auth"
  data_json = jsonencode(
    {
      auth_client_id = random_uuid.auth_client_id.result,
      auth_client_secret = random_uuid.auth_client_secret.result
    }
  )
}
