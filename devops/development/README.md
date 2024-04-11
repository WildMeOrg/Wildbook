# Development Wildbook

This will launch docker containers sufficient for you to deploy a `wildbook.war` file which you have developed
via your own Java environment. <!-- TODO: link/explain java and maven instructions -->

## WBIA / ML

Presently, this deployment does not start a local WBIA (image analysis) docker container. This feature will be
added in future development.

# Docker images used

The following docker containers should launch if started with `docker-compose [-d] up`.

- latest postegresql
- tomcat9


# Setup and running

1. copy `_env.template` to `.env` and edit this new file with your own values
1. deploy your `.war` file in the correct location _(see below)_
1. `docker-compose up`
1. open in browser http://localhost:81/

## .env and war file deployment

You must manually deploy your `.war` file in order for tomcat to find the application and start up correctly.

**Where** you do this depends on
what you have set `WILDBOOK_BASE_DIR` to in your `.env` file. For example, if you have `WILDBOOK_BASE_DIR=/dir/wildbook-dev`,
you would want to first set up these directories:

```
mkdir -p /dir/wildbook-dev/webapps/wildbook
mkdir /dir/wildbook-dev/logs
```

Next, you can just keep deploying the war file and restarting wildbook container as you develop:
```
cd /dir/wildbook-dev/webapps/wildbook
jar -xvf /path/to/wildbook-xxx.war
```

Then, from this directory in the repo, restart tomcat to start using the newly deployed code:

```
docker-compose restart wildbook
```

