# Stage 1: Build Wildbook WAR
FROM maven:3.9.6-eclipse-temurin-21 AS build

# Install build-essential, Node.js and npm
# build-essential is needed for some native npm modules
RUN apt-get update && \
  apt-get install -y build-essential curl gnupg && \
  curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
  apt-get install -y nodejs imagemagick rsync && \
  apt-get clean && rm -rf /var/lib/apt/lists/*

# Set working directory to the application root
WORKDIR /app

COPY local-repo /app/local-repo

# Copy pom.xml and download Maven dependencies
COPY pom.xml ./
RUN mvn verify clean --fail-never

COPY ./frontend /app/frontend
COPY ./src/main/webapp/javascript /app/src/main/webapp/javascript

WORKDIR /app/frontend
ENV PUBLIC_URL=/react/
ENV SITE_NAME="Wildbook"
RUN npm install react-app-rewired
RUN npm ci
RUN npm run build
RUN mkdir -p /app/src/main/webapp/react && rsync -a ./build/ /app/src/main/webapp/react/

WORKDIR /app
COPY . .
# Build the WAR file, skipping tests and front end build for faster build
ENV SKIP_FRONTEND_BUILD=true
RUN mvn -T 4 clean install -DskipTests -Dmaven.antrun.skip=true


# Stage 2: Deploy to Tomcat
FROM tomcat:9.0.113-jre17-temurin-jammy

# Install envsubst for environment variable substitution
RUN apt-get update && apt-get install -y gettext-base imagemagick && rm -rf /var/lib/apt/lists/*


# Create staging location for config files (seeded into mounted volume at runtime)
RUN mkdir -p /opt/wildbook_seed/WEB-INF/classes/bundles

# Create empty AnnotationLiteCache.json file with valid JSON to prevent startup warning
# RUN echo '{}' > /usr/local/tomcat/webapps/wildbook_data_dir/WEB-INF/AnnotationLiteCache.json

# Set environment variables for runtime configuration
ENV JAVA_OPTS="-Djava.awt.headless=true -Xms4096m -Xmx4096m"

# Copy config and bundle files to staging location (seeded into mounted volume at runtime)
COPY --from=build /app/src/main/resources/bundles/ /opt/wildbook_seed/WEB-INF/classes/bundles/

# Copy Docker-specific configuration files to staging
COPY ./devops/development/.dockerfiles/tomcat/server.xml /usr/local/tomcat/conf/server.xml
COPY ./devops/development/.dockerfiles/tomcat/watermark.png /usr/local/tomcat/watermark.png
COPY ./devops/development/.dockerfiles/tomcat/IA-wbia.json /opt/wildbook_seed/WEB-INF/classes/bundles/IA.json
COPY ./devops/development/.dockerfiles/tomcat/IA-wbia.properties /opt/wildbook_seed/WEB-INF/classes/bundles/IA.properties
COPY ./devops/development/.dockerfiles/tomcat/commonConfiguration.properties /opt/wildbook_seed/WEB-INF/classes/bundles/commonConfiguration.properties
COPY ./devops/development/.dockerfiles/tomcat/jdoconfig.properties.template /opt/wildbook_seed/WEB-INF/classes/bundles/jdoconfig.properties.template


# Copy the built WAR file from the build stage to Tomcat's webapps directory as ROOT.war
COPY --from=build /app/target/wildbook-*.war /usr/local/tomcat/webapps/ROOT.war

# Copy startup script
COPY ./devops/development/.dockerfiles/tomcat/wildbook-start.sh /usr/local/bin/wildbook-start.sh
RUN chmod +x /usr/local/bin/wildbook-start.sh

# Expose the port on which Tomcat will run
EXPOSE 8080

# Use custom startup script
CMD ["/usr/local/bin/wildbook-start.sh"]
