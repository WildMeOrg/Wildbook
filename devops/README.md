# Containerized Wildbook Installation

This directory contains the containerized build instructions (docker build files) for creating images for the Wildbook application and a Nginx configuration specifically for Wildbook.

## Build instructions

To build the images run:
    ./build.sh

This will build the Nginx and Wildbook images.

It is not recommended that you upload these images to the container registry. Please leave that to the continuous integration (CI) service.

## Publish instructions

To publish the results use:
    ./publish.sh

This will publish the build images to [Docker Hub](https://hub.docker.com) by default.  You can change the container registry to publish to using the `-r` option. See `./publish.sh -h` for usage details.

Do not run this locally, leave that to the continuous integration (CI) service. See the Nightly workflow in `.github/workflows/nightly.yml` for more information.
