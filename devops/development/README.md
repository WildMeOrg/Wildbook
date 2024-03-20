# Development Wildbook

This will launch docker containers sufficient for you to deploy a `wildbook.war` file which you have developed
via your own Java environment. [see xxx for more details]

# Docker images used

The following docker containers should launch if started with `docker-compose [-d] up`.

- latest postegresql
- tomcat9
- latest wbia _[optional, if you wish to test Wildbook **without** image processing support]_

To skip optional containers, you should launch explicitly including the ones you want, such as: `docker-compose up db wildbook`.


# Setup and running

- copy `_env.template` to `.env` and edit this new file with your own values
- `docker-compose up ....`
- open in browser http://localhost:81/ (wildbook) or http://localhost:82/ (wbia)

## .env and war file deployment

You must manually deploy your `.war` file in order for tomcat to find the application. Where you do this depends on
what you have set `WILDBOOK_BASE_DIR` to in your `.env` file. For example, if you have `WILDBOOK_BASE_DIR=/dir/wildbookdev`,
you would want to first set up these directories:

```
mkdir -p /dir/wildbookdev/webapps/wildbook
mkdir /dir/wildbookdev/logs
```

Then, you can just keep deploying the war file and restarting wildbook container as you develop:
```
cd /dir/wildbookdev/webapps/wildbook
jar -xvf /path/to/wildbook-xxx.war

(cd back to this dir in repo)
docker-compose restart wildbook
```

This should **restart tomcat** with the _new wildbook code deployed_.
