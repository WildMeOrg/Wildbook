# Development Wildbook

This will launch docker containers sufficient for you to deploy a `wildbook.war` file which you have developed
via your own Java environment. <!-- TODO: link/explain java and maven instructions -->

## WBIA / ML

Presently, this deployment does not start a local WBIA (image analysis) docker container. This feature will be
added in future development.


# Setup and running

1. copy `_env.template` to `.env` and edit this new file (if needed)
1. make required directories under your `WILDBOOK_BASE_DIR` (default is `~/wildbook-dev/`):
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
1. run `docker-compose up [-d]` (will launch latest postgresql and tomcat9 docker images)
1. open in browser http://localhost:81/ when tomcat has started

## When developing

As you compile new war files, they can be deployed into the `wildbook` dir (as in step 3 above) and then tomcat restarted with:

```
docker-compose restart wildbook
```

