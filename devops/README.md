# Wildbook Installation

We provide two different containerized Wildbooks using Docker. Unless you are setting up a production instance of Wildbook, use the **Development Docker** option.

## Development Docker

All support materials are found in the `devops/development/` subdirectory. This launches docker containers sufficient for you to deploy a `wildbook.war` file which you have developed via your own Java environment. 

### Overview of docker containers deployed

- **db** - postgresql database for storing Wildbook data
- **wildbook** -- the tomcat (java server) container which runs Wildbook
- **opensearch** -- runs OpenSearch to support searching in Wildbook
- **smtp** -- handles outgoing email (for password reset etc.) It is beyond the scope of this current document to address setting up email relays on the open internet. Environment variables for this are set in `.env` file.
- ~~**wbia**~~ - Presently, this _does not_ start a local WBIA (image analysis) docker container. The current roadmap is focused on removing WBIA as a requirement as we modernize our machine learning tech.

## Prereqs
You need the following installed on your system:
* `default-jdk`
* `build-essential`
* `maven`
* `npm`
* `node` (minimum version: 18)
* `docker-compose`

## System setup
1. Run `sudo sysctl -w vm.max_map_count=262144` (A requirement for OpenSearch, it only needs to be run once on your system.)
1. Run `npm install react-app-rewired`
1. `git clone` the Wildbook repo (referred to as the **code directory** going forward)

### Code directory setup
1. In `devops/development/`, create a `.env` file with a copy the contents of `_env.template`. By default, no changes should be needed.
1. `cd` to the root of the code directory
1. run `npm install`
1. run `chmod +x .husky/pre-commit` to enable husky linting
1. `cd /frontend`
1. run `npm install` to install all dependencies
1. create a `.env` for React environment variables.
    1. Add the public URL: `PUBLIC_URL= /react/`
    1. Add the site name: `SITE_NAME=My Local Wildbook`

### Deploy directory setup
1. Create your **deploy directory**, matching the value of `WILDBOOK_BASE_DIR` in the .env file. The default is `~/wildbook-dev/`)
1. Create the necessary subdirectories
```
wildbook-dev
|--logs
|--webapps
   |--wildbook
```

### Build war file
To run Wildbook in the development docker environment, even to try out the software, you need a "war file" which is made by compiling the Wildbook java project.

To create the war file:
1. `cd` to the root of the code directory
1. Build your war file with `mvn clean install`
1. verify the war file was created in `target/wildbook-X.Y.Z.war` (where X.Y.Z is the current version number).
1. cd to the deploy directory `cd ~/wildbook-dev/webapps/wildbook` 
1. deploy your warfile `jar -xvf /code/directory/Wildbook/target/wildbook-X.Y.Z.war`

### Deploy
1. `cd` to the `devops/development` directory in the code repo
1. run `docker-compose up [-d]`
1. To verify successful launch, open in browser `http://localhost:81/` when tomcat has started. Default login of username/password `tomcat`/`tomcat123` should work.

Note: if you're running docker as root, you may way to explicitly set your deploy directory path to include your user, i.e., `WILDBOOK_BASE_DIR=~USER/wildbook-dev`

### Rebuild war file for local testing

If you make code changes and want to test them locally, you can create and deploy a new war file using the Build war file and Deploy instructions above. Then use `docker-compose restart wildbook` to test your changes.

Note: If you are only making changes to the React frontend code, see the [frontend/README.md](../frontend/README.md) for a way to rebuild and deploy the frontend changes only.

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
