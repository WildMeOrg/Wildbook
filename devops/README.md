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
1. Install build requirements on either your local machine or your vm by entering the following commands:
    ```
    sudo apt-get update && sudo apt-get upgrade
    sudo apt-get install build-essential tomcat9 nginx postgresql
    sudo apt-get install imagemagick
    sudo apt-get install postgresql-10-postgis-2.4 (or latest version)
    sudo apt-get install maven
	  sudo apt install nodejs
	  sudo apt install npm
    ```
  1. Use `node --version` to verify that you're running node 16.0.0. If not, update to that version.
1. Build Wildbook
  1. Make a project folder with `mkdir code`
  1. `cd code` to operate from your new project folder
  1. Clone the Wildbook GitHub repor `git clone https://github.com/WildMeOrg/Wildbook.git`
  1. `cd Wildbook` to operate from your new Wildbook folder
  1. `git checkout <yourbranch>` to checkout your branch
  1. `./mavenBuild.sh` to create a .WAR file, which is used to setup tomcat
  1. Verify the build was successful
  ```
    [INFO] -----------------------------------------------------------------------
    [INFO] BUILD SUCCESS
    [INFO] ------------------------------------------------------------------------
    [INFO] Total time:  02:21 min
    [INFO] Finished at: 2024-07-02T10:52:10-07:00
    [INFO] ------------------------------------------------------------------------
  ```
1. Set up tomcat
  1. `sudo systemctl start tomcat9` to start tomcat
  1. Copy files to tomcat (installed under usr, not your user) `cp wildbook-[version].war /var/lib/tomcat9/webapps/wildbook.war`
  1. `sudo systemctl restart tomcat9` to restart tomcat
  ```
  http://localhost:8080 tomcat is running
  http://localhost:8080/wildbook wildbook is running
  ```
  1. To access `http://localhost:8080/`:
  1. Open tomcat's `conf/server.xml`
  1. Add the docBase contexts to the `<Host>...</Host>` block
  ```
    <Host>
	    <Context docBase="wildbook" path="" />
	    <Context docBase="wildbook_data_dir" path="/wildbook_data_dir" />
    </Host>
  ```
1. Set up react
  TBD
1. Create the wildbook database in postgres
  1. `sudo -u postgres psql`
  1. `CREATE USER [dbuser] WITH PASSWORD '[password]';`
  1. `CREATE DATABASE [dbname] WITH OWNER [dbuser];`
  1. `GRANT ALL PRIVILEGES ON DATABASE [dbname] TO [dbuser];`
  1. `\c [dbname];`
  1. `CREATE EXTENSION "uuid-ossp";`
  1. `CREATE EXTENSION "postgis";`
1. Startup should set up the local AssetStore, the repository for images that users upload to accompany sightings.
  1. Verify that there is one entry in the “ASSETSTORE” table by visiting `localhost:8080/appadmin/editAssetStore.jsp`
  1. If there is not one entry, manual initialize the AssetStore
    - TBD
1. Initialize some FeatureTypes.
  - `psql -U [dbuser] -h localhost [dbname] < /[repo-path]/config/feature_types.sql`
1. Add the database indices.
  - `psql -U [dbuser] -h localhost [dbname] < /[repo-path]/config/indices.sql`
1. Edit the AssetStore
  1. stop tomcat
  1. edit in `/var/lib/tomcat8/webapps/wildbook/appadmin/editAssetStore.jsp`
    - Change the id = 999 line to id = 1 if not already done.
    - Change the line `newConfig.put("webroot", "http://example.com/wildbook_data_dir");` to `http://localhost:8080/wildbook_data_dir`
  1. restart tomcat
  1. Re-build and run tomcat8 anew
  1. navigate to https://yourUrl.com/appadmin/editAssetStore.jsp.
  1. execute the new configuration changes
    - `sudo chown -R tomcat8:tomcat8 /data/wildbook_data_dir`
1. copy `REPO/config/image*.sh` into `/usr/local/bin/`
  - Test by submitting an encounter with an image.
  - Confirm that you can see the image on the `encounter.jsp` page.
1. You may need to set the local asset store’s USAGE column with value, ‘default’, so that other asset stores don’t compete with it:
  - `UPDATE “ASSETSTORE” set “USAGE”=’default’ where “NAME”=’Local AssetStore’;` (or something similar if that’s not correct)
