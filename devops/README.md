# Wildbook Installation

We provide two different containerized Wildbooks using Docker. Unless you are setting up a production instance of Wildbook, use the **Development Docker** option.

## Development Docker

All support materials are found in the `devops/development/` subdirectory. This launches docker containers sufficient for you to deploy a `wildbook.war` file which you have developed
via your own Java environment. 

### Overview of docker containers deployed

- **db** - postgresql database for storing Wildbook data
- **wildbook** -- the tomcat (java server) container which runs Wildbook
- **opensearch** -- runs OpenSearch to support searching in Wildbook
- **smtp** -- handles outgoing email (for password reset etc.) It is beyond the scope of this current document to address setting up email relays on the open internet.
Environment variables for this are set in `.env` file - see below.
- ~~**wbia**~~ - Presently, this _does not_ start a local WBIA (image analysis) docker container. The current roadmap is focused on removing WBIA as a requirement as we modernize our machine learning tech.

### Setup and running

1. Run `sudo sysctl -w vm.max_map_count=262144` (A requirement for OpenSearch, it only needs to be run once on your system.)
1. In `devops/development/` folder, create a `.env` file with a copy the contents of `_env.template`. By default, no changes should be needed, but you can edit this new file if needed.
1. In your terminal, create your base directory (value of `WILDBOOK_BASE_DIR` from `.env` file above) and the required subdirectories. The default is `~/wildbook-dev/`). For example:
	```
	mkdir -p ~/wildbook-dev/webapps/wildbook
	mkdir ~/wildbook-dev/logs
	```
1. Deploy your `.war` file (see section below) in the above `wildbook/` directory, using `jar`:
	```
	cd ~/wildbook-dev/webapps/wildbook
	jar -xvf /path/to/wildbook-xxx.war
	```
1. Return to the `devops/development/` directory in the wildbook repo
1. Run `docker-compose up [-d]`, which launches all of the aforementioned docker images
1. To verify successful launch, open in browser http://localhost:81/ when tomcat has started. Default login of username/password `tomcat`/`tomcat123` should work.

### Development environment setup for compiling Wildbook

To run Wildbook in the development docker environment, even to try out the software, you need a "war file" which is made by compiling the Wildbook java project.
This requires some software to be set up on your development machine:

- Java JDK (`openjdk`) and `build-essential` linux package, as well as `maven` <span style="background-color: yellow;">[probably a link to generic setup doc elsewhere?]</span>
- node and npm for React build <span style="background-color: yellow;">[likewise, link to generic help?]</span>, more details in [frontend/README.md](../frontend/README.md).

#### Compiling

Once the above requirements are met, the war file can be created by running `mvn clean install`. This creates the war file to be used in `target/wildbook-X.Y.Z.war` (with current version number).

If you make code changes and compile new war files, they can be deployed into the `wildbook` dir (as in step 3 above) and then tomcat restarted with
`docker-compose restart wildbook`.

---

## Deploy (e.g. Production) Docker Image - DRAFT

This image and all support materials are found in the `deploy/` subdirectory. Run Wildbook and required docker images for production installations only.

**THIS IS CURRENTLY UNDER DEVELOPMENT - DRAFT ONLY**

This launches an instance of Wildbook for the sake of testing or using in production.
It can be used to deploy on a VM/host on the internet or locally.

### Docker images used

The following docker containers should launch if started with `docker-compose [-d] up`. Some of these are "optional" as noted below.

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

## Local tomcat 
If you want to run Wildbook on non-dockerized tomcat, the system will likely build, but functionality will be restricted (i.e., search will not work) and additional functionality will likely break as we continue modernizing the stack. That being said, good luck!
