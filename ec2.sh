#!/bin/bash

cp /home/ubuntu/Wildbook_javaTweetBot/twitter.properties /home/ubuntu/Wildbook_javaTweetBot/src/main/resources/bundles/twitter.properties
cp /opt/tomcat/webapps/wildbook/tweetFind.jsp /home/ubuntu/Wildbook_javaTweetBot/src/main/webapp/

rm -rf /opt/tomcat/webapps/wildbook.war

rm -rf /opt/tomcat/webapps/wildbook

rm -rf /opt/tomcat/logs/catalina.out

mvn clean install -DskipTests -Dmaven.javadoc.skip=true && cp /home/ubuntu/Wildbook_javaTweetBot/target/wildbook-6.0.0-EXPERIMENTAL.war /opt/tomcat/webapps/wildbook.war

cd /opt; sudo chmod -R 777 tomcat

