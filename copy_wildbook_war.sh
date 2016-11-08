#!/bin/bash

service tomcat7 stop
cp ~/documents/Wildbook/target/wildbook-6.0.0-EXPERIMENTAL.war ~/../../var/lib/tomcat7/webapps/wildbook.war
service tomcat7 start
