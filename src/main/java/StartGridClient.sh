#! /bin/sh
 cd ~/opt/tomcat/webapps/wildbook/WEB-INF/lib
 java -classpath .:javax.jdo-3.2*.jar:commons-math3-*.jar:datanucleus-api-jdo-4.*.jar:datanucleus-core-4.1*.jar -Xms256m -Xmx4096m org.ecocean.grid.WorkAppletHeadlessEpic http://34.209.17.78
