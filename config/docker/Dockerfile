FROM tomcat:8.5-alpine

RUN mkdir -p /tmp/ScheduledQueue

COPY ./target/wildbook-6.0.0-EXPERIMENTAL.war /usr/local/tomcat/webapps/wildbook.war
COPY ./config/docker/start.sh ./bin/start.sh
COPY ./config/docker/jdoconfig.properties ./webapps/wildbook_data_dir/WEB-INF/classes/bundles/jdoconfig.properties

EXPOSE 8080
EXPOSE 8009

CMD ["start.sh"]
