FROM tomcat:8.5-jre8 as org.wildme.wildbook.base

MAINTAINER Wild Me <dev@wildme.org>

ARG AZURE_DEVOPS_CACHEBUSTER=0

RUN echo "ARGS AZURE_DEVOPS_CACHEBUSTER=${AZURE_DEVOPS_CACHEBUSTER}"

# Install apt packages
RUN apt-get update \
 && apt-get install -y \
        imagemagick \
 && rm -rf /var/lib/apt/lists/*

##########################################################################################
FROM openjdk:8u171-jdk as org.wildme.wildbook.build

# The arg recieves the value from the build script --build-args, but swapping for an
# env allows it to keep this value after the current container is built.
ARG branch=master
ENV branch=${branch}

# Install apt packages
RUN apt-get update \
 && apt-get install -y \
        git=1:2.11.0-3+deb9u3 \
        maven=3.3.9-4 \
 && rm -rf /var/lib/apt/lists/*

# Create ibeis source location
RUN mkdir -p /wildbook \
    && cd wildbook \
    && git clone https://github.com/WildbookOrg/Wildbook.git

# Did that arg make it?
RUN echo ${branch}

# Make sure we get the correct branch and most recent version of it.
RUN cd wildbook/Wildbook/ \
 && git checkout -f ${branch} \
 && git pull

# Copy branch specific build files.
COPY ./_config/${branch}/jdoconfig.properties /wildbook/Wildbook/src/main/resources/bundles/jdoconfig.properties
COPY ./_config/${branch}/commonConfiguration.properties /wildbook/Wildbook/src/main/resources/bundles/commonConfiguration.properties

# Build and move the war file, skipping tests and other verbosity
RUN cd wildbook/Wildbook \
 && mvn clean install -DskipTests -Dmaven.javadoc.skip=true -B \
 && cp target/wildbook-*.war /wildbook/wildbook.war

##########################################################################################
FROM org.wildme.wildbook.base as org.wildme.wildbook.install

ARG branch=master
ENV branch=${branch}

RUN mkdir -p /tmp/ScheduledQueue

RUN rm -rf /usr/local/tomcat/webapps/ROOT/

ADD ./_config/${branch}/context.xml /usr/local/tomcat/conf/context.xml

ADD ./_config/${branch}/server.xml /usr/local/tomcat/conf/server.xml

COPY --from=org.wildme.wildbook.build /wildbook/wildbook.war /usr/local/tomcat/webapps/wildbook.war

RUN mkdir -p /data/ \
 && mkdir -p /data/wildbook_data_dir \
 && ln -s /data/wildbook_data_dir /usr/local/tomcat/webapps/wildbook_data_dir

VOLUME /data/wildbook_data_dir

##########################################################################################
FROM org.wildme.wildbook.install as org.wildme.wildbook.deploy

ENTRYPOINT ["/usr/local/tomcat/bin/catalina.sh"]

CMD ["run", "-Xms4096m", "Xmx4096m"]

EXPOSE 8080
EXPOSE 8009

STOPSIGNAL SIGTERM
