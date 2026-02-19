# Use the base Tomcat image
FROM tomcat:9.0.113-jre17-temurin-jammy

RUN mkdir -p /usr/local/tomcat/webapps/wildbook_data_dir/WEB-INF/classes/bundles

# Set environment variables
ENV DB_USER=${WILDBOOK_DB_USER}
ENV DB_PASSWORD=${WILDBOOK_DB_PASSWORD}
ENV DB_CONNECTION_URL=${WILDBOOK_DB_CONNECTION_URL}
ENV ADMIN_EMAIL=${EDM_AUTHENTICATIONS_USERNAME__DEFAULT}
ENV ADMIN_PASSWORD=${EDM_AUTHENTICATIONS_PASSWORD__DEFAULT}
ENV SERVER_URL=${SERVER_URL}
ENV JAVA_OPTS="-Djava.awt.headless=true -Xms4096m -Xmx4096m"

# Copy configuration files into the image
COPY .dockerfiles/docker-entrypoint.sh /docker-entrypoint.sh
COPY .dockerfiles/tomcat/server.xml /usr/local/tomcat/conf/server.xml
COPY .dockerfiles/tomcat/watermark.png /usr/local/tomcat/watermark.png
COPY .dockerfiles/tomcat/IA.json /usr/local/tomcat/webapps/wildbook_data_dir/WEB-INF/classes/bundles/IA.json
COPY .dockerfiles/tomcat/IA.properties /usr/local/tomcat/webapps/wildbook_data_dir/WEB-INF/classes/bundles/IA.properties
COPY .dockerfiles/tomcat/commonConfiguration.properties /usr/local/tomcat/webapps/wildbook_data_dir/WEB-INF/classes/bundles/commonConfiguration.properties

# Set the entrypoint script
ENTRYPOINT ["/docker-entrypoint.sh"]

# Expose the port on which Tomcat will run
EXPOSE 8080

# Default command (inherited from base image)
CMD ["catalina.sh", "run"]
