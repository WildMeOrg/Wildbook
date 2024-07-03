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
  1. `Sudo systemctl restart tomcat9` to restart tomcat
  ```
  http://localhost:8080 tomcat is running
  http://localhost:8080/wildbook wildbook is running
  ```
1. Creating a wildbook database in postgres
  1. `sudo -u postgres psql`
  1. CREATE USER wildbook(or whatever you want to call your database) WITH PASSWORD 'wildbook';
  1. CREATE DATABASE wildbook(or whatever you want to call your database) WITH OWNER wildbook;
  1. GRANT ALL PRIVILEGES ON DATABASE wildbook(or whatever you want to call your database) TO wildbook;
  1. \c wildbook(or whatever you want to call your database);
  1. CREATE EXTENSION "uuid-ossp";
  1. CREATE EXTENSION "postgis";
1. Setting Up Your AssetStore
You need to set up an AssetStore (probably local). This is the repository for images that users upload to accompany sightings. Startup Wildbook might do this for you, but you should 
  1. check that it is doing what it should (basically you should have one entry in the “ASSETSTORE” table) This check can be accomplished by visiting `https://yourWildbookUrl/appadmin/editAssetStore.jsp`
  1. There are some FeatureTypes that need to be initialized just once:
    1. `psql -U dbuser -h localhost dbname < repo/config/feature_types.sql`
  1. There are (db) indices you can add, similarly: `psql -U dbuser -h localhost dbname < repo/config/indices.sql`
■ E.g., `sudo psql -U wildbook -h localhost wildbook <
/data/code/Wildbook/config/indices.sql`
○ Note that when starting a new Wildbook, this table won’t yet exist
○ You will eventually want to run this again after that table gets made
○ It’s the kind of thing that will only matter when a wildbook grows huge after lots of
IA tasks getting run.
● Open editAssetStore.jsp
(/data/code/Wildbook/src/main/webapp/appadmin/editAssetStore.jsp and rebuild and
then copy over to tomcat or simply stop tomcat8, edit in
/var/lib/tomcat8/webapps/wildbook/appadmin/editAssetStore.jsp, and restart tomcat8)
● Change the id = 999 line to id = 1 if not already done.
● Change the line newConfig.put("webroot", "http://example.com/wildbook_data_dir"); to
your URL/wildbook_data_dir
● Re-build and run tomcat8 anew, and navigate to
https://yourUrl.com/appadmin/editAssetStore.jsp. If the changes look correct, execute
the new configuration changes
● Run: `sudo chown -R tomcat8:tomcat8 /data/wildbook_data_dir`
● copy REPO/config/image*.sh into /usr/local/bin/
● Test it out by submitting an encounter (and uploading an image with it). Confirm that you
can see the image on the encounter.jsp page.
● You may need to set the local asset store’s USAGE column with value, ‘default’, so that
other asset stores don’t compete with it:
○ `UPDATE “ASSETSTORE” set “USAGE”=’default’ where “NAME”=’Local
AssetStore’; (or something similar if that’s not correct)

## Troubleshooting
- Try stopping and restarting tomcat8.
- Is ownership of /var/lib/tomcat8/webapps/whatever_data_dir tomcat8:tomcat8?
- commonConfigs were missing properties:defaultProjectOrganizationParameter (resulting in NPE)
- Check hardcoded path to wildbook_data_dir in LocationID.java
- Are IA.json and IA.properties pointing to the same WBIA? Is it the correct one?
- Is `<Context path="" docBase="wildbook_or_whatever_you_want_to_call_it" debug="0">` in /var/lib/tomcat8/conf/server.xml configured in a desirable way? If there are other WB instances on the same server, consider removing or changing the path of the others.
- Are there several instances of different wildbooks in /var/lib/tomcat8/webapps? Consider temporarily moving the ones you’re not working with out of that directory to streamline troubleshooting the log files.
- Have you removed old files from /data/wildbook_data_dir or analogous directories?
- Have you tried to run Wilbook/archive/setup.jsp, or more specifically one line, `CommonConfiguration.ensureServerInfo(myShepherd, request)` from that file? This line will replace the URL currently being referenced in the database with the current browser url.
- Does /var/lib/tomcat8/webapps/wildbook exist? There is some hardcoding looking for this in ShepherdProperties.java.



1. To access `http://localhost:8080/`:
  1. Open tomcat's `conf/server.xml`
  1. Add the docBase contexts to the `<Host>...</Host>` block
  ```
    <Host>
	    <Context docBase="wildbook" path="" />
	    <Context docBase="wildbook_data_dir" path="/wildbook_data_dir" />
    </Host>
  ```
