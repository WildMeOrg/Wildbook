#!/bin/bash

service tomcat7 stop
rm /var/lib/tomcat7/webapps/wildbook.war
rm -rf /var/lib/tomcat7/webapps/Wildbook
cp ~/documents/Wildbook/target/wildbook-6.0.0-EXPERIMENTAL.war ~/../../var/lib/tomcat7/webapps/wildbook.war
service tomcat7 start
service tomcat7 restart
