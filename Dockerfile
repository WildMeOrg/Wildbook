# Stage 1: Build Wildbook WAR (Java only)
FROM maven:3.9.6-eclipse-temurin-21 AS java-build

RUN apt-get update && \
  apt-get install -y build-essential curl gnupg && \
  curl -fsSL https://deb.nodesource.com/setup_18.x | bash - && \
  apt-get install -y nodejs rsync && \
  apt-get clean && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY local-repo /app/local-repo
COPY pom.xml ./
RUN mvn verify clean --fail-never

# Copy the entire project (frontend source is harmless now that the pom.xml plugin is removed)
COPY . /app

# Remove any stale React build artifacts from previous hybrid builds
RUN rm -rf /app/src/main/webapp/react

ENV SKIP_FRONTEND_BUILD=true
RUN mvn -T 4 clean install -DskipTests -Dmaven.antrun.skip=true

# Stage 2: Deploy to Tomcat
FROM tomcat:9.0.113-jre17-temurin-jammy

RUN apt-get update && apt-get install -y gettext-base imagemagick && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt/wildbook_seed/WEB-INF/classes/bundles

ENV JAVA_OPTS="-Djava.awt.headless=true -Xms4096m -Xmx4096m"

COPY --from=java-build /app/src/main/resources/bundles/ /opt/wildbook_seed/WEB-INF/classes/bundles/
COPY ./devops/development/.dockerfiles/tomcat/server.xml /usr/local/tomcat/conf/server.xml
COPY ./devops/development/.dockerfiles/tomcat/watermark.png /usr/local/tomcat/watermark.png
COPY ./devops/development/.dockerfiles/tomcat/IA-wbia.json /opt/wildbook_seed/WEB-INF/classes/bundles/IA.json
COPY ./devops/development/.dockerfiles/tomcat/IA-wbia.properties /opt/wildbook_seed/WEB-INF/classes/bundles/IA.properties
COPY ./devops/development/.dockerfiles/tomcat/commonConfiguration.properties /opt/wildbook_seed/WEB-INF/classes/bundles/commonConfiguration.properties
COPY ./devops/development/.dockerfiles/tomcat/jdoconfig.properties.template /opt/wildbook_seed/WEB-INF/classes/bundles/jdoconfig.properties.template

COPY --from=java-build /app/target/wildbook-*.war /usr/local/tomcat/webapps/ROOT.war

COPY ./devops/development/.dockerfiles/tomcat/wildbook-start.sh /usr/local/bin/wildbook-start.sh
RUN chmod +x /usr/local/bin/wildbook-start.sh

EXPOSE 8080
CMD ["/usr/local/bin/wildbook-start.sh"]
