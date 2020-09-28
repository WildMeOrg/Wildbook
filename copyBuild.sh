#!/bin/bash

echo \ * copying resources...
sudo service tomcat8 stop
sudo cp -R target/wildbook..../* /var/lib/tomcat8/webapps/wildbook/
sudo service tomcat8 start
echo \ * copyBuild complete.
