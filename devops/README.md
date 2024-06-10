# Wildbook Installation

We provide two different containerized Wildbooks using Docker. Unless you are setting up a production instance of Wildbook, use the `development` image. Wildbook can also be set up as a local tomcat application.

## Development Docker Image

This image and all support materials are found in the `development/` subdirectory. Run docker images necessary to launch _wildbook.war_ file developed via java/maven

This will launch docker containers sufficient for you to deploy a `wildbook.war` file which you have developed
via your own Java environment. <!-- TODO: link/explain java and maven instructions -->

### WBIA / ML

Presently, this deployment does not start a local WBIA (image analysis) docker container. This feature will be added in future development.

### Setup and running

1. `sudo sysctl -w vm.max_map_count=262144` (this only needs to be run once on your system)
1. In this folder, create a `.env` file and copy the contents of `_env.template` to it. By default, no changes should be needed, but you can edit this new file.
1. In your terminal, create your base directory (value of `WILDBOOK_BASE_DIR`) and the required subdirectories. The default is `~/wildbook-dev/`):
	```
	mkdir -p ~/wildbook-dev/webapps/wildbook
	mkdir ~/wildbook-dev/logs
	```
1. deploy your `.war` file in the `wildbook/` directory, using `jar`:
	```
	cd ~/wildbook-dev/webapps/wildbook
	jar -xvf /path/to/wildbook-xxx.war
	```
1. return to the `devops/development/` directory in the wildbook repo
1. run `docker-compose up [-d]`, which will launch latest postgresql and tomcat9 docker images
1. To verify successful launch, open in browser http://localhost:81/ when tomcat has started

### When developing

As you compile new war files, they can be deployed into the `wildbook` dir (as in step 3 above) and then tomcat restarted with:

```
docker-compose restart wildbook
```

## Deploy Docker Image - DRAFT

This image and all support materials are found in the `deploy/` subdirectory. Run Wildbook and required docker images for production installations only.

**THIS IS CURRENTLY UNDER DEVELOPMENT - DRAFT ONLY**

This will launch an instance of Wildbook for the sake of testing or using in production.
It can be used to deploy on a VM/host on the internet or locally.

### Docker images used

The following docker containers should launch if started with `docker-compose [-d] up`. Some of these
are "optional" as noted below.

- latest postegresql
- tomcat9 / latest wildbook
- latest wbia _[optional, if you wish to test Wildbook **without** image processing support]_
- latest nginx _[optional, especially if running locally]_

To skip optional containers, you should launch explicitly including the ones you want, such as: `docker-compose up db wildbook`.

#### nginx and SSL certs

Currently, nginx is not configured to support ssl/https certs. There are some notes in the nginx conf about potential solutions.

### Setup and running

- copy `_env.template` to `.env` and edit this new file with your own values
- `docker-compose up ....`

## Local Tomcat 
**TODO #503: draft local tomcat instructions**

If you are running tomcat locally (not using docker), in order to access it as `http://localhost:8080/` (rather than with `/wildbook` trailing directory),
you should modify the `<Host>...</Host>` block of tomcat's `conf/server.xml` to contain the following:

```
	<Context docBase="wildbook" path="" />
	<Context docBase="wildbook_data_dir" path="/wildbook_data_dir" />
```
