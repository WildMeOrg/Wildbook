FROM tomcat:8.5-jre8 as org.wildme.wildbook.base

MAINTAINER Wild Me <dev@wildme.org>

ARG AZURE_DEVOPS_CACHEBUSTER=0

RUN echo "ARGS AZURE_DEVOPS_CACHEBUSTER=${AZURE_DEVOPS_CACHEBUSTER}"

# Install apt packages
RUN apt-get update \
 && rm -rf /var/lib/apt/lists/*

##########################################################################################
FROM openjdk:8u171-jdk as org.wildme.wildbook.build

# Install apt packages
RUN apt-get update \
 && apt-get install -y \
        git=1:2.11.0-3+deb9u3 \
        maven=3.3.9-4 \
 && rm -rf /var/lib/apt/lists/*

COPY . /wildbook/Wildbook

# Copy branch specific build files.
COPY ./.dockerfiles/config/jdoconfig.properties /wildbook/Wildbook/src/main/resources/bundles/jdoconfig.properties
COPY ./.dockerfiles/config/commonConfiguration.properties /wildbook/Wildbook/src/main/resources/bundles/commonConfiguration.properties

# Build and move application directory, skipping tests and other verbosity
RUN cd wildbook/Wildbook \
 && mvn clean install -DskipTests -Dmaven.javadoc.skip=true \
 && mkdir -p wildbook \
 && cp -r target/wildbook-*/ wildbook

##########################################################################################
FROM org.wildme.wildbook.base as org.wildme.wildbook.install

RUN rm -rf /usr/local/tomcat/webapps/ROOT/

ADD ./.dockerfiles/config/context.xml /usr/local/tomcat/conf/context.xml

ADD ./.dockerfiles/config/server.xml /usr/local/tomcat/conf/server.xml

RUN mkdir -p /usr/local/tomcat/webapps/wildbook

COPY --from=org.wildme.wildbook.build wildbook/Wildbook/wildbook/* /usr/local/tomcat/webapps/wildbook/

RUN mkdir -p /data/ \
 && mkdir -p /data/wildbook_data_dir \
 && ln -s /data/wildbook_data_dir /usr/local/tomcat/webapps/wildbook_data_dir

VOLUME /data/wildbook_data_dir

##########################################################################################
FROM org.wildme.wildbook.install as org.wildme.wildbook.deploy

#Allow DB args from startup script
ARG DB_USER=wildbook
ENV echo "DB_USER=${DB_USER}"

ARG DB_PASSWORD=wildbook
ENV echo "DB_PASSWORD=${DB_PASSWORD}"

ARG DB_DRIVER_NAME=org.postgresql.Driver
ENV echo "DB_DRIVER_NAME=${DB_DRIVER_NAME}"

ARG DB_CONNECTION_URL=jdbc:postgresql://localhost:5432/wildbook
ENV echo "DB_CONNECTION_URL=${DB_CONNECTION_URL}"

COPY ./.dockerfiles/config/jdoconfig.properties /usr/local/tomcat/webapps/wildbook_data_dir/WEB-INF/classes/bundles/jdoconfig.properties

ENTRYPOINT ["/usr/local/tomcat/bin/catalina.sh"]

CMD ["run", "-Xms4096m", "Xmx4096m"]

EXPOSE 8080

STOPSIGNAL SIGTERM
