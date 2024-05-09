./shutdown.sh

docker network create main

chmod -R 700 wildbook_db_postgresql/

docker container run \
    -d \
    --name wildbook_db_postgresql \
    --network=main \
    --network-alias postgresql \
    -e POSTGRES_DB=wildbook \
    -e POSTGRES_USER=wildbook \
    -e POSTGRES_PASSWORD=wildbook \
    -v "$(pwd)"/wildbook_db_postgresql:/var/lib/postgresql/data:z \
    postgres:9.6

docker container run \
    -d \
    --name wildbook_tomcat \
    --network=main \
    --network-alias wildbook \
    --link wildbook_db_postgresql \
    -v "$(pwd)"/wildbook_tomcat_data_dir:/data/wildbook_data_dir/ \
    wildme/wildbook:latest

docker container run \
    -d \
    -p 80:80 \
    --name wildbook_nginx \
    --network=main \
    --network-alias nginx \
    --link wildbook_tomcat \
    wildme/nginx:latest
