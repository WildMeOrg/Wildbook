#!/bin/bash

echo \ ** stopping tomcat...
sudo service tomcat8 stop &&
	echo \ *** removing deployed application dir from tomcat webapps directory...&&
	sudo rm -rf /var/lib/tomcat8/webapps/ncaquariums &&
	echo \ **** copying new application dir to tomcat webapps directory... &&
	sudo cp -r target/wildbook-7.0.0-EXPERIMENTAL /var/lib/tomcat8/webapps/ncaquariums &&
	echo \ ***** changing permissions to /var/lib/tomcat8/webapps/ncaquariums... &&
	sudo chown -R tomcat8:tomcat8 /var/lib/tomcat8/webapps/ncaquariums &&
	echo \ ***** restarting tomcat... &&
	sudo service tomcat8 restart &&
	echo \ * application deployed                              
