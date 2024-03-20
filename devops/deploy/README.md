# Deployable Wildbook

⚠️  **This directory is currently in development! Not for general use.** ⚠️

This will launch an instance of Wildbook for the sake of testing or using in production.

## Docker images used

The following docker containers should launch if started with `docker-compose [-d] up`. Some of these
are "optional" as noted below.

- latest postegresql
- tomcat9 / latest wildbook
- latest wbia _[optional, if you wish to test Wildbook **without** image processing support]_
- latest nginx _[optional, especially if running locally]_

To skip optional containers, you should launch explicitly including the ones you want, such as: `docker-compose up db wildbook`.


### nginx and SSL certs

Currently, nginx is not configured to support ssl/https certs. There are some notes in the nginx conf about potential solutions.

# Setup and running

- copy `_env.template` to `.env` and edit this new file with your own values
- `docker-compose up ....`

