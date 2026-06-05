#!/bin/bash

# Check if vault container is running
if docker ps -q -f name=vault | grep -q .; then
    echo "Stopping Vault container..."
    docker stop vault
    sleep 3
    echo "Vault container stopped."
fi