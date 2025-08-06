
# FROM node:22 as react-builder

# Set working directory

# WORKDIR /app/frontend

# COPY frontend/ .

# ENV PUBLIC_URL=/react/

# ENV SITE_NAME="Test Site Name"

# RUN npm install && npm run build

# RUN mkdir -p /app/war_output/react && mv build/* /app/war_output/react/

FROM maven:3.6-jdk-8 as builder

WORKDIR /app

# COPY --from=react-builder /app/war_output/react /app/war_output/react  

# Set Java and Maven options for Java 8
ENV MAVEN_OPTS="-Xmx256m"
ENV JAVA_TOOL_OPTIONS="-Xmx256m"


# Build the project using Maven
COPY pom.xml /app/pom.xml
COPY local-repo /app/local-repo
COPY config /app/config

RUN mvn -Dmaven.repo.local=/app/local-repo dependency:go-offline -B || true

RUN mvn -Dmaven.repo.local=/app/local-repo \
        org.apache.maven.plugins:maven-compiler-plugin:3.6.1:help \
 &&     mvn -Dmaven.repo.local=/app/local-repo \
        org.codehaus.mojo:exec-maven-plugin:1.2:help \
 &&     mvn -Dmaven.repo.local=/app/local-repo \
        org.apache.tomcat.maven:tomcat7-maven-plugin:2.2:help \
 &&     mvn -Dmaven.repo.local=/app/local-repo \
        org.apache.maven.plugins:maven-war-plugin:2.2:help
 
COPY . /app

# Now run Maven build
RUN mvn -Dmaven.repo.local=/app/local-repo clean install \
    -DskipTests \
    -Dmaven.javadoc.skip=true \
    -Dhttp.keepAlive=false \
    -Dmaven.wagon.http.pool=false \
    --batch-mode \
    -B

RUN mkdir -p /app/war_output && \
    cp target/*.war /app/war_output/wildbook.war && \
    cd /app/war_output && \
    jar -xf wildbook.war

# Build the runtime image
FROM tomcat:9.0.85-jre8-temurin-jammy

RUN mkdir -p /usr/local/tomcat/webapps/wildbook_data_dir

COPY --from=builder /app/war_output /usr/local/tomcat/webapps/wildbook

COPY devops/development/.dockerfiles/docker-entrypoint.sh /docker-entrypoint.sh
COPY devops/development/.dockerfiles/tomcat/server.xml /usr/local/tomcat/conf/server.xml
COPY devops/development/.dockerfiles/tomcat/watermark.png /usr/local/tomcat/watermark.png
COPY devops/development/.dockerfiles/tomcat/IA-wbia.json /efs-init/bundles/IA.json
COPY devops/development/.dockerfiles/tomcat/IA-wbia.properties /efs-init/bundles/IA.properties
COPY devops/development/.dockerfiles/tomcat/commonConfiguration.properties /efs-init/bundles/commonConfiguration.properties

# Set environment variables
ENV JAVA_OPTS="-Djava.awt.headless=true -Xms4096m -Xmx4096m"
ENV JPDA_ADDRESS="8000"
ENV JPDA_TRANSPORT="dt_socket"

# Expose ports
EXPOSE 8080 8000

# Set entrypoint
ENTRYPOINT ["/docker-entrypoint.sh"]