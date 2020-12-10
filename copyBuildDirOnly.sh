#!/bin/bash

echo \ ** stopping tomcat...
sudo service tomcat8 stop &&
	echo \ *** removing deployed application dir from tomcat webapps directory...&&
	sudo rm -rf /var/lib/tomcat8/webapps/mm &&
	echo \ **** copying new application dir to tomcat webapps directory... &&
	sudo cp -r target/wildbook-7.0.0-EXPERIMENTAL /var/lib/tomcat8/webapps/mm &&
	echo \ ***** restarting tomcat... &&
	sudo service tomcat8 restart &&
	echo \ * application deployed                              
