FROM nginx:1.15.0 as org.wildme.nginx.deploy

MAINTAINER Wild Me <dev@wildme.org>

ARG AZURE_DEVOPS_CACHEBUSTER=0

RUN echo "ARGS AZURE_DEVOPS_CACHEBUSTER=${AZURE_DEVOPS_CACHEBUSTER}"

# The arg recieves the value from the build script --build-args, but swapping for an
# env allows it to keep this value after the current container is built.
ARG branch=master
ENV branch=${branch}

ADD ./_config/${branch}/nginx.conf /etc/nginx/nginx.conf
