#!/bin/sh

# bad code begets worse code
mkdir /data
ln -s /usr/local/tomcat/webapps/wildbook_data_dir /data/

apt-get update -qq
apt-get install -y -qq imagemagick
#apt-get update
#apt-get install -y imagemagick

# Copy files from image to EFS-mounted directory (only if not already copied)
if [ -d /efs-init/bundles ]; then
  echo "Copying files to EFS..."
  cp -u /efs-init/bundles/* /usr/local/tomcat/webapps/wildbook_data_dir/WEB-INF/classes/bundles/
  cp -rf /usr/local/tomcat/webapps/wildbook_data_dir/properties/* /usr/local/tomcat/webapps/wildbook/WEB-INF/classes/bundles/
  echo "✅ Files copied to EFS."
else
  echo "⚠️ No init files found in /efs-init/bundles"
fi

echo Done pre-initializing Wildbook.

# now run tomcat normally
exec $CATALINA_HOME/bin/catalina.sh jpda run
