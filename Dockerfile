FROM tomcat:8.5-alpine

COPY ./target/wildbook-6.0.0-EXPERIMENTAL.war /usr/local/tomcat/webapps/wildbook.war

EXPOSE 8080
EXPOSE 8009

