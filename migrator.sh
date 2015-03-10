#!/bin/sh 

java -cp ".;C:/github-shepherd/target/wildbook-5.2.0-RELEASE/WEB-INF/lib/*;C:/github-shepherd/target/wildbook-5.2.0-RELEASE/WEB-INF/classes;C:/github-shepherd/target/wildbook-5.2.0-RELEASE/WEB-INF/classes/bundles" com.jholmberg.GADolphinImporter "C:/GADolphin/GA CFS PID Data/GACFS.PID.Data.Sheet2014.12.16MD.xlsx" "C:/GADolphin/GA CFS Catalog 2014.10.14" "C:/apache-tomcat-8.0.5/webapps/shepherd_data_dir"

java -cp ".:/opt/tomcat7/webapps/dev/WEB-INF/lib/*:/opt/tomcat7/webapps/dev/classes:/opt/tomcat7/webapps/dev/bundles" com.jholmberg.GADolphinImporter "/home/webadmin/GADolphin/GA CFS PID Data/GACFS.PID.Data.Sheet 2015.02.10 MD.xlsx" "/home/webadmin/GADolphin/GA CFS Catalog 2014.10.14" "/opt/tomcat7/webapps/dev_data_dir"
