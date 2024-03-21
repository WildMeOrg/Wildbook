#!/bin/sh

# bad code begets worse code
mkdir /data
ln -s /usr/local/tomcat/webapps/wildbook_data_dir /data/

#apt-get update -qq
#apt-get install -y -qq imagemagick
apt-get update
apt-get install -y imagemagick
echo Done pre-initializing Wildbook.

# now run tomcat normally
$CATALINA_HOME/bin/catalina.sh run
