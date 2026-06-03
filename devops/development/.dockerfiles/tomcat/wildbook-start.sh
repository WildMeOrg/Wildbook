#!/bin/bash
# Set defaults for database configuration
export WILDBOOK_DB_CONNECTION_URL=${WILDBOOK_DB_CONNECTION_URL:-"jdbc:postgresql://db:5432/wildbook"}
export WILDBOOK_DB_USER=${WILDBOOK_DB_USER:-"wildbook"}
export WILDBOOK_DB_PASSWORD=${WILDBOOK_DB_PASSWORD:-"development"}

# Seed config files from staging into mounted wildbook_data_dir on first boot
DATA_DIR=/usr/local/tomcat/webapps/wildbook_data_dir
SEED_DIR=/opt/wildbook_seed

if [ ! -f "$DATA_DIR/WEB-INF/classes/bundles/commonConfiguration.properties" ]; then
  echo "Seeding configuration into $DATA_DIR..."
  mkdir -p "$DATA_DIR/WEB-INF/classes/bundles"
  cp -a "$SEED_DIR/." "$DATA_DIR/"
fi

# Create symlink for legacy path compatibility
mkdir -p /data
ln -sf "$DATA_DIR" /data/wildbook_data_dir
mkdir -p "$DATA_DIR/WEB-IN"

# Symlink legacy "wildbook" context to ROOT (watermark path, etc.)
ln -sf /usr/local/tomcat/webapps/ROOT /usr/local/tomcat/webapps/wildbook

# Create required runtime directories on persisted volume
mkdir -p "$DATA_DIR/encounters"
mkdir -p "$DATA_DIR/users"
mkdir -p "$DATA_DIR/upload"

# Substitute environment variables in jdoconfig.properties
envsubst < "$DATA_DIR/WEB-INF/classes/bundles/jdoconfig.properties.template" > "$DATA_DIR/WEB-INF/classes/bundles/jdoconfig.properties"

# Fix containerName for WBIA communication
sed -i 's/^containerName=.*/containerName=wildbook/' "$DATA_DIR/WEB-INF/classes/bundles/commonConfiguration.properties"

# Override SMTP host for MailHog
sed -i 's/^mailHost=.*/mailHost=mailhog/' "$DATA_DIR/WEB-INF/classes/bundles/commonConfiguration.properties"

# Start Tomcat
exec catalina.sh run
