#!/usr/bin/env bash
set -e

# Script to build the Wildbook WAR and deploy it to the development Docker environment

# Determine script and project directories
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Change to project root
cd "$PROJECT_ROOT"

echo "Building Wildbook WAR..."
mvn clean install -DskipTests

# Locate the WAR file
WAR_FILE=$(ls "$PROJECT_ROOT"/target/*.war | head -n1)
if [[ ! -f "$WAR_FILE" ]]; then
  echo "Error: WAR file not found in target/"
  exit 1
fi

# Load environment variables
ENV_FILE="$SCRIPT_DIR/.env"
if [[ ! -f "$ENV_FILE" ]]; then
  echo "Error: .env file not found at $ENV_FILE"
  exit 1
fi
# shellcheck disable=SC1090
source "$ENV_FILE"

# Resolve ~ in WILDBOOK_BASE_DIR
DEPLOY_ROOT="${WILDBOOK_BASE_DIR/#\~/$HOME}"
DEPLOY_DIR="$DEPLOY_ROOT/webapps/wildbook"

echo "Deploying WAR to $DEPLOY_DIR"
mkdir -p "$DEPLOY_DIR"
rm -rf "$DEPLOY_DIR"/*
cd "$DEPLOY_DIR" && jar -xvf "$WAR_FILE"

echo "Deployment complete to $DEPLOY_DIR"
