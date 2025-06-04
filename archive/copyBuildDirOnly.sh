#!/bin/bash

echo \ ** stopping tomcat...
sudo service tomcat8 stop &&
	echo \ *** removing deployed application dir from tomcat webapps directory...&&
	sudo rm -rf /var/lib/tomcat8/webapps/giraffe &&
	echo \ **** copying new application dir to tomcat webapps directory... &&
	sudo cp -r target/wildbook-7.0.0-EXPERIMENTAL /var/lib/tomcat8/webapps/giraffe &&
	echo \ ***** changing permissions to /var/lib/tomcat8/webapps/giraffe... &&
	sudo chown -R tomcat8:tomcat8 /var/lib/tomcat8/webapps/giraffe &&
	echo \ ***** restarting tomcat... &&
	sudo service tomcat8 restart &&
	echo \ * application deployed
