#!/bin/bash

cp /usr/local/apache-tomcat-7.0.79/webapps/wildbook/tweetFind.jsp ~/Desktop/temp/Wildbook_javaTweetBot/src/main/webapp/

rm -rf /usr/local/apache-tomcat-7.0.79/webapps/wildbook.war

rm -rf /usr/local/apache-tomcat-7.0.79/webapps/wildbook

rm -rf /usr/local/apache-tomcat-7.0.79/logs/catalina.out

mvn clean install -DskipTests -Dmaven.javadoc.skip=true && cp /Users/mf/Desktop/temp/Wildbook_javaTweetBot/target/wildbook-6.0.0-EXPERIMENTAL.war /usr/local/apache-tomcat-7.0.79/webapps/wildbook.war

