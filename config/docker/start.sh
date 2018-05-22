#!/usr/bin/env bash

set -e

WEB_APP_DIR=/usr/local/tomcat/webapps
CONFIG_DIR=wildbook_data_dir/WEB-INF/classes/bundles
CONFIG_FILE=$WEB_APP_DIR/$CONFIG_DIR/jdoconfig.properties

: ${DB_CONNECTION_URL:="jdbc:postgresql://localhost:5432/wildbook"}
echo "datanucleus.ConnectionURL = $DB_CONNECTION_URL" >> $CONFIG_FILE

catalina.sh run
