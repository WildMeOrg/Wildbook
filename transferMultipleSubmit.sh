#!/bin/bash

service tomcat stop
cp ~/Wildbook/src/main/webapp/javascript/multipleSubmit/* /opt/tomcat/webapps/wildbook/javascript/multipleSubmit/
cp ~/Wildbook/src/main/webapp/multipleSubmit/multipleSubmit.jsp /opt/tomcat/webapps/wildbook/multipleSubmit/
cp ~/Wildbook/src/main/webapp/css/multipleSubmit.css /opt/tomcat/webapps/wildbook/css/multipleSubmit.css
service tomcat start