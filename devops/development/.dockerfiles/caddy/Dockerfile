# Stage 1: Build frontend assets independently
FROM node:18 AS frontend-build

WORKDIR /frontend

COPY ./frontend/package*.json ./
COPY ./frontend/. ./

# Tablesorter assets referenced by frontend CSS via relative paths
COPY ./src/main/webapp/javascript/tablesorter/ /src/main/webapp/javascript/tablesorter/

ENV PUBLIC_URL=/react/
ENV SITE_NAME="Wildbook"
RUN npm install react-app-rewired
RUN npm ci
RUN npm run build

# Stage 2: Caddy proxy with built React static files
FROM caddy:2-alpine

COPY --from=frontend-build /frontend/build /srv/react
COPY devops/development/.dockerfiles/caddy/Caddyfile /etc/caddy/Caddyfile
