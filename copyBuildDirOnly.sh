#!/bin/bash

echo \ ** stopping tomcat...
sudo service tomcat8 stop &&
	echo \ *** removing deployed application dir from tomcat webapps directory...&&
	sudo rm -rf /var/lib/tomcat8/webapps/wildbook &&
	echo \ **** copying new application dir to tomcat webapps directory... &&
	sudo cp -r target/wildbook-6.0.0-FINAL /var/lib/tomcat8/webapps/wildbook &&
	echo \ ***** changing permissions to /var/lib/tomcat8/webapps/wildbook... &&
	sudo chown -R tomcat8:tomcat8 /var/lib/tomcat8/webapps/wildbook &&
	echo \ ***** restarting tomcat... &&
	sudo service tomcat8 restart &&
	echo \ * application deployed                              
