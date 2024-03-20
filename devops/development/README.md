# Development Wildbook

This will launch docker containers sufficient for you to deploy a `wildbook.war` file which you have developed
via your own Java environment. [see xxx for more details]

## Docker images used

The following docker containers should launch if started with `docker-compose [-d] up`. Some of these
are "optional" as noted below.

- latest postegresql
- tomcat9
- latest wbia _[optional, if you wish to test Wildbook **without** image processing support]_

To skip optional containers, you should launch explicitly including the ones you want, such as: `docker-compose up db wildbook`.


# Setup and running

- copy `_env.template` to `.env` and edit this new file with your own values
- `docker-compose up ....`

