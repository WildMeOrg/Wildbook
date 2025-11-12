# Wildbook Local Development Makefile
# This Makefile automates the setup process for local Wildbook development

# Force bash shell for color codes and modern shell features
SHELL := /bin/bash

.PHONY: help setup build deploy start stop stop-volumes init-db db-shell build-and-run check-prerequisites setup-directories setup-environment create-wbia-json restore-wbia-json clean

# Colors for output (use with printf or echo -e)
RED := \\033[0;31m
GREEN := \\033[0;32m
YELLOW := \\033[1;33m
NC := \\033[0m

# Define directories
WILDBOOK_DEV_DIR := $(HOME)/wildbook-dev
WEBAPPS_DIR := $(WILDBOOK_DEV_DIR)/webapps/wildbook
DATA_DIR := $(WILDBOOK_DEV_DIR)/webapps/wildbook_data_dir
LOGS_DIR := $(WILDBOOK_DEV_DIR)/logs
ENV_TEMPLATE := devops/development/_env.template
ENV_FILE := devops/development/.env
DOCKER_DIR := devops/development
COMPOSE_FILE := docker-compose-local.yml

# Default target
help:
	@echo "Wildbook Local Development Setup"
	@echo ""
	@echo "Usage: make [target]"
	@echo ""
	@echo "Targets:"
	@echo "  setup             - Run initial setup (directories and environment)"
	@echo "  build             - Build the project with Maven"
	@echo "  deploy            - Deploy the built WAR file"
	@echo "  start             - Start Docker containers"
	@echo "  stop              - Stop Docker containers"
	@echo "  stop-volumes      - Stop Docker containers and remove volumes"
	@echo "  init-db           - Initialize database with required configuration"
	@echo "  db-shell          - Open PostgreSQL terminal in database container"
	@echo "  build-and-run     - Build, deploy, and start everything"
	@echo "  create-wbia-json  - Convert WBIA endpoints for local Docker routing"
	@echo "  restore-wbia-json - Restore WBIA JSON from backup"
	@echo "  clean             - Remove all local development files"
	@echo "  help              - Show this help message"
	@echo ""
	@echo "Examples:"
	@echo "  make setup          # First time setup"
	@echo "  make build-and-run  # Build and start everything"
	@echo "  make init-db        # Initialize database (run if build-and-run was interrupted)"
	@echo "  make db-shell       # Open PostgreSQL terminal"
	@echo "  make stop           # Stop the containers"

# Check prerequisites
check-prerequisites:
	@echo ""
	@echo "========================================"
	@echo "Checking Prerequisites"
	@echo "========================================"
	@command -v mvn >/dev/null 2>&1 || { echo -e "$(RED)✗ Maven is not installed$(NC)"; \
		echo -e "$(YELLOW)ℹ Please install Maven:$(NC)"; \
		echo "  - On Linux: sudo apt update && sudo apt install maven"; \
		echo "  - On Mac: brew install maven"; \
		exit 1; }
	@echo -e "$(GREEN)✓ Maven is installed: $$(mvn -version | head -n 1)$(NC)"
	@command -v docker >/dev/null 2>&1 || { echo -e "$(RED)✗ Docker is not installed$(NC)"; \
		echo -e "$(YELLOW)ℹ Please install Docker from https://www.docker.com/get-started$(NC)"; \
		exit 1; }
	@echo -e "$(GREEN)✓ Docker is installed$(NC)"
	@docker compose version >/dev/null 2>&1 || { echo -e "$(RED)✗ Docker Compose is not available$(NC)"; exit 1; }
	@echo -e "$(GREEN)✓ Docker Compose is available$(NC)"

# Setup directory structure
setup-directories:
	@echo ""
	@echo "========================================"
	@echo "Setting up Directory Structure"
	@echo "========================================"
	@mkdir -p "$(WEBAPPS_DIR)"
	@echo -e "$(GREEN)✓ Created: $(WEBAPPS_DIR)$(NC)"
	@mkdir -p "$(DATA_DIR)"
	@echo -e "$(GREEN)✓ Created: $(DATA_DIR)$(NC)"
	@mkdir -p "$(LOGS_DIR)"
	@echo -e "$(GREEN)✓ Created: $(LOGS_DIR)$(NC)"

create-wbia-json:
	@echo ""
	@echo "========================================"
	@echo "Converting WBIA JSON Endpoints"
	@echo "========================================"
	@OS_TYPE=$$(uname -s); \
	if [ "$$OS_TYPE" = "Linux" ]; then \
		echo -e "$(YELLOW)ℹ Detected Linux - using Docker internal routing (172.17.0.1:8000)$(NC)"; \
		DOCKER_HOST="172.17.0.1:8000"; \
	elif [ "$$OS_TYPE" = "Darwin" ]; then \
		echo -e "$(YELLOW)ℹ Detected macOS - using host.docker.internal:8000$(NC)"; \
		DOCKER_HOST="host.docker.internal:8000"; \
	else \
		echo -e "$(RED)✗ Unsupported OS: $$OS_TYPE$(NC)"; \
		exit 1; \
	fi; \
	JSON_FILE="devops/development/.dockerfiles/tomcat/IA-wbia.json"; \
	if [ ! -f "$$JSON_FILE" ]; then \
		echo -e "$(RED)✗ JSON file not found: $$JSON_FILE$(NC)"; \
		exit 1; \
	fi; \
	echo -e "$(YELLOW)ℹ Updating endpoints in $$JSON_FILE$(NC)"; \
	sed -i.bak \
		-e "s|http://172\.31\.26\.160:5000/api/|http://$$DOCKER_HOST/|g" \
		-e "s|https://h4s1noaaz0\.execute-api\.us-east-1\.amazonaws\.com/api/|http://$$DOCKER_HOST/|g" \
		"$$JSON_FILE"; \
	echo -e "$(GREEN)✓ Endpoints updated successfully$(NC)"; \
	echo -e "$(YELLOW)ℹ Backup created: $$JSON_FILE.bak$(NC)"

restore-wbia-json:
	@echo ""
	@echo "========================================"
	@echo "Restoring WBIA JSON from Backup"
	@echo "========================================"
	@JSON_FILE="devops/development/.dockerfiles/tomcat/IA-wbia.json"; \
	BACKUP_FILE="$$JSON_FILE.bak"; \
	if [ ! -f "$$BACKUP_FILE" ]; then \
		echo -e "$(RED)✗ Backup file not found: $$BACKUP_FILE$(NC)"; \
		echo -e "$(YELLOW)ℹ No backup available to restore$(NC)"; \
		exit 1; \
	fi; \
	echo -e "$(YELLOW)ℹ Restoring from backup: $$BACKUP_FILE$(NC)"; \
	cp "$$BACKUP_FILE" "$$JSON_FILE"; \
	rm "$$BACKUP_FILE"; \
	echo -e "$(GREEN)✓ File restored successfully$(NC)"; \
	echo -e "$(GREEN)✓ Backup file removed$(NC)"

# Setup environment file
setup-environment: create-wbia-json
	@echo ""
	@echo "========================================"
	@echo "Setting up Environment File"
	@echo "========================================"
	@if [ -f "$(ENV_FILE)" ]; then \
		echo -e "$(YELLOW)ℹ Environment file already exists at: $(ENV_FILE)$(NC)"; \
		echo -e "$(YELLOW)ℹ Skipping environment file creation$(NC)"; \
	elif [ ! -f "$(ENV_TEMPLATE)" ]; then \
		echo -e "$(RED)✗ Template file not found: $(ENV_TEMPLATE)$(NC)"; \
		exit 1; \
	else \
		cp "$(ENV_TEMPLATE)" "$(ENV_FILE)"; \
		echo -e "$(GREEN)✓ Created environment file: $(ENV_FILE)$(NC)"; \
		echo -e "$(YELLOW)ℹ Please edit $(ENV_FILE) and configure:$(NC)"; \
		echo "  - WILDBOOK_DB_NAME"; \
		echo "  - WILDBOOK_DB_USER"; \
		echo "  - WILDBOOK_DB_PASSWORD"; \
		echo "  - WILDBOOK_DB_CONNECTION_URL"; \
	fi

# Build the project
build: check-prerequisites
	@echo ""
	@echo "========================================"
	@echo "Building the Project"
	@echo "========================================"
	@echo -e "$(YELLOW)ℹ Running Maven build script...$(NC)"
	@./mavenBuild.sh
	@echo -e "$(GREEN)✓ Build completed successfully$(NC)"

# Deploy the application
deploy:
	@echo ""
	@echo "========================================"
	@echo "Deploying the Application"
	@echo "========================================"
	@WAR_FILE=$$(find target -name "wildbook-*.war" | head -n 1); \
	if [ -z "$$WAR_FILE" ]; then \
		echo -e "$(RED)✗ WAR file not found in target$(NC)"; \
		echo -e "$(YELLOW)ℹ Please run 'make build' first$(NC)"; \
		exit 1; \
	fi; \
	WAR_FILENAME=$$(basename "$$WAR_FILE"); \
	echo -e "$(YELLOW)ℹ Found WAR file: $$WAR_FILENAME$(NC)"; \
	echo -e "$(YELLOW)ℹ Cleaning deployment directory...$(NC)"; \
	rm -rf "$(WEBAPPS_DIR)"; \
	mkdir -p "$(WEBAPPS_DIR)"; \
	echo -e "$(YELLOW)ℹ Copying WAR file...$(NC)"; \
	cp "$$WAR_FILE" "$(WEBAPPS_DIR)/"; \
	echo -e "$(YELLOW)ℹ Extracting WAR file...$(NC)"; \
	cd "$(WEBAPPS_DIR)" && jar -xf "$$WAR_FILENAME"; \
	echo -e "$(GREEN)✓ Application deployed to: $(WEBAPPS_DIR)$(NC)"

# Start Docker containers
start:
	@echo ""
	@echo "========================================"
	@echo "Starting Docker Containers"
	@echo "========================================"
	@if [ ! -f "$(ENV_FILE)" ]; then \
		echo -e "$(RED)✗ Environment file not found: $(ENV_FILE)$(NC)"; \
		echo -e "$(YELLOW)ℹ Please run 'make setup' first$(NC)"; \
		exit 1; \
	fi
	@echo -e "$(YELLOW)ℹ Starting Docker Compose...$(NC)"
	@cd $(DOCKER_DIR) && docker compose -f $(COMPOSE_FILE) up -d --remove-orphans
	@echo -e "$(GREEN)✓ Docker containers started$(NC)"

# Stop Docker containers
stop:
	@echo ""
	@echo "========================================"
	@echo "Stopping Docker Containers"
	@echo "========================================"
	@echo -e "$(YELLOW)ℹ Stopping Docker Compose...$(NC)"
	@cd $(DOCKER_DIR) && docker compose -f $(COMPOSE_FILE) down
	@echo -e "$(GREEN)✓ Docker containers stopped$(NC)"

# Stop Docker containers and remove volumes
stop-volumes:
	@echo ""
	@echo "========================================"
	@echo "Stopping Docker Containers and Removing Volumes"
	@echo "========================================"
	@echo -e "$(YELLOW)ℹ Stopping Docker Compose and removing volumes...$(NC)"
	@cd $(DOCKER_DIR) && docker compose -f $(COMPOSE_FILE) down -v
	@echo -e "$(GREEN)✓ Docker containers stopped and volumes removed$(NC)"

# Open database terminal
db-shell:
	@echo ""
	@echo "========================================"
	@echo "Opening Database Terminal"
	@echo "========================================"
	@if [ ! -f "$(ENV_FILE)" ]; then \
		echo -e "$(RED)✗ Environment file not found: $(ENV_FILE)$(NC)"; \
		echo -e "$(YELLOW)ℹ Please run 'make setup' first$(NC)"; \
		exit 1; \
	fi
	@. $(ENV_FILE) && \
	echo -e "$(YELLOW)ℹ Connecting to database: $$WILDBOOK_DB_NAME as user: $$WILDBOOK_DB_USER$(NC)" && \
	echo -e "$(YELLOW)ℹ Type 'exit' or press Ctrl+D to exit the database terminal$(NC)" && \
	echo "" && \
	cd $(DOCKER_DIR) && docker compose -f $(COMPOSE_FILE) exec db psql -U "$$WILDBOOK_DB_USER" -d "$$WILDBOOK_DB_NAME"

# Initialize database with required data
init-db:
	@echo ""
	@echo "========================================"
	@echo "Initializing Database"
	@echo "========================================"
	@if [ ! -f "$(ENV_FILE)" ]; then \
		echo -e "$(RED)✗ Environment file not found: $(ENV_FILE)$(NC)"; \
		exit 1; \
	fi
	@. $(ENV_FILE) && \
	echo -e "$(YELLOW)ℹ Waiting for database to be ready...$(NC)" && \
	max_attempts=60; \
	attempt=0; \
	while [ $$attempt -lt $$max_attempts ]; do \
		if (cd $(DOCKER_DIR) && docker compose -f $(COMPOSE_FILE) exec -T db pg_isready -U postgres >/dev/null 2>&1); then \
			echo -e "$(GREEN)✓ Database is ready$(NC)"; \
			break; \
		fi; \
		attempt=$$((attempt + 1)); \
		if [ $$attempt -eq $$max_attempts ]; then \
			echo -e "$(RED)✗ Database did not become ready in time$(NC)"; \
			exit 1; \
		fi; \
		sleep 2; \
	done && \
	echo -e "$(YELLOW)ℹ Waiting for SYSTEMVALUE table to be created (this may take a while)...$(NC)" && \
	attempt=0; \
	max_attempts=120; \
	while [ $$attempt -lt $$max_attempts ]; do \
		if (cd $(DOCKER_DIR) && docker compose -f $(COMPOSE_FILE) exec -T db psql -U "$$WILDBOOK_DB_USER" -d "$$WILDBOOK_DB_NAME" -c "\dt public.\"SYSTEMVALUE\"" 2>/dev/null) | grep -q "SYSTEMVALUE"; then \
			echo -e "$(GREEN)✓ SYSTEMVALUE table exists$(NC)"; \
			break; \
		fi; \
		attempt=$$((attempt + 1)); \
		if [ $$attempt -eq $$max_attempts ]; then \
			echo -e "$(RED)✗ SYSTEMVALUE table was not created in time$(NC)"; \
			echo -e "$(YELLOW)ℹ The table may be created later by the application. You can run: make init-db$(NC)"; \
			exit 1; \
		fi; \
		sleep 5; \
	done && \
	echo -e "$(YELLOW)ℹ Inserting SERVER_INFO configuration...$(NC)" && \
	(cd $(DOCKER_DIR) && docker compose -f $(COMPOSE_FILE) exec -T db psql -U "$$WILDBOOK_DB_USER" -d "$$WILDBOOK_DB_NAME" -c "INSERT INTO public.\"SYSTEMVALUE\" (\"KEY\", \"VALUE\", \"VERSION\") VALUES('SERVER_INFO', '{\"type\":\"JSONObject\",\"value\":{\"scheme\":\"http\",\"contextPath\":\"\",\"context\":\"context0\",\"serverName\":\"localhost\",\"serverPort\":81,\"timestamp\":1584020902808}}', 1584020902808) ON CONFLICT (\"KEY\") DO NOTHING;")
	@echo -e "$(GREEN)✓ Database initialized successfully$(NC)"

# Full setup
setup: check-prerequisites setup-directories setup-environment
	@echo ""
	@echo "========================================"
	@echo "Initial Setup Complete"
	@echo "========================================"
	@echo -e "$(YELLOW)ℹ Next steps:$(NC)"
	@echo "  1. Edit the environment file: $(ENV_FILE)"
	@echo "  2. Run: make build-and-run"

# Build and run
build-and-run: clean check-prerequisites setup-directories setup-environment build deploy start init-db
	@echo ""
	@echo "========================================"
	@echo "Wildbook is Running!"
	@echo "========================================"
	@echo -e "$(GREEN)✓ Access the application at: http://localhost:81/$(NC)"
	@echo ""
	@echo -e "$(YELLOW)ℹ Post-Setup Tasks:$(NC)"
	@echo "  1. Update API URLs in: devops/development/.dockerfiles/tomcat/IA-wbia.json"
	@echo "     Change http://172.17.0.1:5000 to your arguswild-api endpoint"

# Clean local development files
clean:
	@echo ""
	@echo "========================================"
	@echo "Cleaning Local Development Files"
	@echo "========================================"
	@echo -e "$(YELLOW)ℹ This will remove all files in $(WILDBOOK_DEV_DIR)$(NC)"
	@read -p "Are you sure? (y/N) " -n 1 -r; \
	echo; \
	if [ "$$REPLY" = "y" ] || [ "$$REPLY" = "Y" ]; then \
		mvn clean; \
		sudo rm -rf "$(WILDBOOK_DEV_DIR)"; \
		echo -e "$(GREEN)✓ Cleaned: $(WILDBOOK_DEV_DIR)$(NC)"; \
	else \
		echo -e "$(YELLOW)ℹ Cancelled$(NC)"; \
	fi

logs:
	@echo ""
	@echo "========================================"
	@echo "Viewing Logs"
	@echo "========================================"
	@echo -e "$(YELLOW)ℹ Viewing logs...$(NC)"
	@cd $(DOCKER_DIR) && docker compose -f $(COMPOSE_FILE) logs -f
	@echo -e "$(GREEN)✓ Logs viewed$(NC)"
