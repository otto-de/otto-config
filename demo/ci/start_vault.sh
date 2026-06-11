#!/bin/bash

# Check if vault container is running
if docker ps -q -f name=vault | grep -q .; then
    echo "Stopping Vault container..."
    docker stop vault
    sleep 3
    echo "Vault container stopped."
fi

# Start Vault in dev mode
docker run --rm --cap-add=IPC_LOCK \
  -e 'VAULT_DEV_ROOT_TOKEN_ID=myroot' \
  -e 'VAULT_DEV_LISTEN_ADDRESS=0.0.0.0:8200' \
  -p 8200:8200 \
  -d --name vault hashicorp/vault:latest

# Wait for Vault to start
sleep 3

# Enable the cftsearch KV v2 mount
docker exec -e VAULT_ADDR='http://127.0.0.1:8200' -e VAULT_TOKEN='myroot' vault vault secrets enable -path=cftsearch kv-v2

# Enable AppRole auth method
docker exec -e VAULT_ADDR='http://127.0.0.1:8200' -e VAULT_TOKEN='myroot' vault vault auth enable approle

# Create a policy
echo 'path "cftsearch/data/*" {
  capabilities = ["create", "read", "update", "delete", "list"]
}
path "cftsearch/metadata/*" {
  capabilities = ["list", "read"]
}' | docker exec -i -e VAULT_ADDR='http://127.0.0.1:8200' -e VAULT_TOKEN='myroot' vault vault policy write otto-config-policy -

# Create an AppRole
docker exec -e VAULT_ADDR='http://127.0.0.1:8200' -e VAULT_TOKEN='myroot' vault vault write auth/approle/role/otto-config \
    policies="otto-config-policy" \
    token_ttl=6m \
    token_max_ttl=10m

# Get role_id
VAULT_ROLE_ID=$(docker exec -e VAULT_ADDR='http://127.0.0.1:8200' -e VAULT_TOKEN='myroot' vault vault read -field=role_id auth/approle/role/otto-config/role-id)
echo "role_id: $VAULT_ROLE_ID"

# Get secret_id
VAULT_SECRET_ID=$(docker exec -e VAULT_ADDR='http://127.0.0.1:8200' -e VAULT_TOKEN='myroot' vault vault write -field=secret_id -f auth/approle/role/otto-config/secret-id)
echo "secret_id: $VAULT_SECRET_ID"

# Write a test secret
docker exec -e VAULT_ADDR='http://127.0.0.1:8200' -e VAULT_TOKEN='myroot' vault vault kv put cftsearch/service/otto-config/develop/auth auth_client_id=admin auth_client_secret=secret

# Test AppRole authentication and reading
echo ""
echo "===== Testing AppRole Authentication ====="
APP_TOKEN=$(docker exec -e VAULT_ADDR='http://127.0.0.1:8200' vault vault write -field=token auth/approle/login role_id=$VAULT_ROLE_ID secret_id=$VAULT_SECRET_ID)
echo "AppRole token: $APP_TOKEN"

echo ""
echo "===== Testing read with AppRole token (raw API) ====="
docker exec -e VAULT_ADDR='http://127.0.0.1:8200' -e VAULT_TOKEN=$APP_TOKEN vault vault read cftsearch/data/service/otto-config/develop/auth

if [ $? -eq 0 ]; then
    echo ""
    echo "✓ AppRole can successfully read the secret!"
else
    echo ""
    echo "✗ AppRole CANNOT read the secret (403 error)"
fi

echo "Vault is running at http://localhost:8200"
echo "Root token: myroot"
echo "AppRole role_id: $VAULT_ROLE_ID"
echo "AppRole secret_id: $VAULT_SECRET_ID"

> .env
echo "VAULT_ROLE_ID=$VAULT_ROLE_ID" >> .env
echo "VAULT_SECRET_ID=$VAULT_SECRET_ID" >> .env