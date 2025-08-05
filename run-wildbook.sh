#!/usr/bin/env bash
set -e

# Script to run Wildbook in Docker development environment
# Handles port conflicts and ensures clean startup

# Determine script and project directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "Starting Wildbook development environment..."

# Check if .env file exists
ENV_FILE="$SCRIPT_DIR/devops/development/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Creating .env file from template..."
  cp "$SCRIPT_DIR/devops/development/_env.template" "$ENV_FILE"
  echo "ES_THRESHOLD=true" >> "$ENV_FILE"
  echo "Created .env file. Please review and modify if needed."
fi

# Check for port conflicts and stop conflicting containers
# Ensure all services are stopped before starting to ensure a clean slate
echo "Stopping any existing Wildbook services..."
cd "$SCRIPT_DIR/devops/development"
if command -v "docker" &> /dev/null && docker compose version &> /dev/null; then
  docker compose down --timeout 30
elif command -v "docker-compose" &> /dev/null; then
  docker-compose down --timeout 30
fi

echo "Checking for port conflicts..."

# Check port 5433 (PostgreSQL)
POSTGRES_CONFLICT=$(docker ps --format "table {{.Names}}\t{{.Ports}}" | grep "5433" | grep -v "development-db" || true)
if [[ -n "$POSTGRES_CONFLICT" ]]; then
  echo "Found conflicting PostgreSQL container(s) on port 5433:"
  echo "$POSTGRES_CONFLICT"
  CONTAINER_NAME=$(echo "$POSTGRES_CONFLICT" | awk '{print $1}')
  echo "Stopping conflicting container: $CONTAINER_NAME"
  docker stop "$CONTAINER_NAME"
fi

# Check port 9200 (OpenSearch)
OPENSEARCH_CONFLICT=$(docker ps --format "table {{.Names}}\t{{.Ports}}" | grep "9200" | grep -v "development-opensearch" || true)
if [[ -n "$OPENSEARCH_CONFLICT" ]]; then
  echo "Found conflicting OpenSearch container(s) on port 9200:"
  echo "$OPENSEARCH_CONFLICT"
  CONTAINER_NAME=$(echo "$OPENSEARCH_CONFLICT" | awk '{print $1}')
  echo "Stopping conflicting container: $CONTAINER_NAME"
  docker stop "$CONTAINER_NAME"
fi

# Check port 81 (Wildbook/Tomcat)
TOMCAT_CONFLICT=$(docker ps --format "table {{.Names}}\t{{.Ports}}" | grep "81" | grep -v "development-wildbook" || true)
if [[ -n "$TOMCAT_CONFLICT" ]]; then
  echo "Found conflicting Tomcat container(s) on port 81:"
  echo "$TOMCAT_CONFLICT"
  CONTAINER_NAME=$(echo "$TOMCAT_CONFLICT" | awk '{print $1}')
  echo "Stopping conflicting container: $CONTAINER_NAME"
  docker stop "$CONTAINER_NAME"
fi

# Build and deploy the WAR file
echo "Building and deploying Wildbook WAR..."
bash "$SCRIPT_DIR/deploy-backend.sh"

# Start Docker Compose services
echo "Starting Docker Compose services..."
cd "$SCRIPT_DIR/devops/development"

# Use docker compose (newer CLI) if available, fallback to docker-compose
if command -v "docker" &> /dev/null && docker compose version &> /dev/null; then
  docker compose up -d
elif command -v "docker-compose" &> /dev/null; then
  docker-compose up -d
else
  echo "Error: Neither 'docker compose' nor 'docker-compose' is available"
  exit 1
fi

# Wait for services to be ready
echo "Waiting for services to start..."
sleep 10

# Check service status
echo "Checking service status..."
if command -v "docker" &> /dev/null && docker compose version &> /dev/null; then
  docker compose ps
else
  docker-compose ps
fi

echo ""
echo "Wildbook development environment is starting up!"
echo "Services:"
echo "  - Wildbook: http://localhost:81"
echo "  - OpenSearch: http://localhost:9200"
echo "  - PostgreSQL: localhost:5433"
echo ""
echo "Default login: tomcat/tomcat123"
echo ""
echo "Note: It may take a few minutes for all services to be fully ready."
echo "Check the logs with: docker compose logs -f wildbook"
