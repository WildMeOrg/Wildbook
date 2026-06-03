FROM node:18

WORKDIR /frontend

RUN npm install -g serve

COPY ./frontend/package*.json ./
RUN npm ci

COPY ./frontend/. ./

# Tablesorter assets referenced by frontend CSS via relative paths
COPY ./src/main/webapp/javascript/tablesorter/ /src/main/webapp/javascript/tablesorter/

ENV PUBLIC_URL=/react/
ENV SITE_NAME="Wildbook"

EXPOSE 3000

# Development: CRA dev server with livereload
CMD ["npm", "start"]
