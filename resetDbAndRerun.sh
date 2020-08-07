#!/bin/bash

echo \ ** stopping tomcat...
sudo service tomcat8 stop &&
	echo \ *** loading database...&&
	sudo zcat /data/backups/postgresql/flukebook-live-db-2020-08-04.sql.gz | psql -U wildbook -h localhost caribwhale &&
	echo \ ***** restarting tomcat... &&
	sudo service tomcat8 restart &&
	echo \ * application deployed                              
	tail -f /var/lib/tomcat8/logs/catalina.out
